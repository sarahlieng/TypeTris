package typetris.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.RadialGradientPaint;
import java.sql.SQLException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;

import typetris.backend.AuthRepository;
import typetris.backend.Leaderboard;
import typetris.backend.PasswordUtil;
import typetris.backend.PlayerAccount;
import typetris.gameplay.GameplayPanel;

/**
 * Desktop shell for Type-tris: one {@link JFrame} with a {@link CardLayout} root.
 * <p>
 * Screens mirror the former static HTML flow: main menu, login/register, logged-in player hub,
 * statistics, leaderboard, instructions, parental PIN gate + admin table, and real gameplay
 * ({@link GameplayPanel}) after difficulty selection.
 * <p>
 * Screens are styled with {@link TypeTrisTheme} to match the Tailwind pages under {@code web/}
 * (those HTML files remain the reference and optional {@link typetris.backend.WebServer} UI).
 * All navigation is local to this class via string card names (see {@code CARD_*} constants).
 */
public final class UserInterface extends JFrame {

    // --- Card names (must match root.add(..., name)) ---

    private static final String CARD_MENU = "menu";
    private static final String CARD_LOGIN = "login";
    private static final String CARD_REGISTER = "register";
    private static final String CARD_PLAYER = "player";
    private static final String CARD_DIFFICULTY = Screens.DIFFICULTY;
    private static final String CARD_GAMEPLAY = Screens.GAMEPLAY;
    private static final String CARD_GAME_OVER = Screens.GAME_OVER;
    private static final String CARD_STATS = "stats";
    private static final String CARD_LEADERBOARD = "leaderboard";
    private static final String CARD_INSTRUCTIONS = "instructions";
    private static final String CARD_PARENT = "parent";
    private static final String CARD_PARENT_DASH = "parentDash";

    /** demo PIN for parent/teacher dashboard */
    private static final String ADMIN_PIN = "2212";

    private final AuthRepository auth;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    /** currently authenticated username, or {@code null} on main menu after logout. */
    private String loggedInUser;

    private final JLabel playerWelcome = new JLabel(" ");

    // personal stats row

    private final JLabel statsHigh = new JLabel("—");
    private final JLabel statsWpm = new JLabel("—");
    private final JLabel statsAcc = new JLabel("—");

    private final DefaultTableModel leaderboardModel =
            new DefaultTableModel(new Object[] { "Rank", "Username", "High score", "Best WPM", "Avg accuracy %" }, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    private final DefaultTableModel parentModel =
            new DefaultTableModel(new Object[] { "Username", "Email", "Sessions", "Last session" }, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    private final JTable parentTable = new JTable(parentModel);

    /** Host panel for {@link GameplayPanel}; swapped on each new run. */
    private final JPanel gameplayHost = new JPanel(new BorderLayout());

    /** Active session, or {@code null} when not on the gameplay card. */
    private GameplayPanel activeGameplay;

    /** Last-started tier / sub-level (1–3); used for restart and new {@link GameplayPanel} instances. */
    private String gameTier = "easy";

    private int gameSubLevel = 1;

    /** [tier index 0–2][level index 0–2] for unlock refresh. */
    private final JButton[][] diffButtons = new JButton[3][3];

    private final JLabel gameOverScore = new JLabel("0");

    public UserInterface(AuthRepository auth) {
        this.auth = auth;
        setTitle("TYPE-TRIS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(720, 520));

        root.add(buildMenuPanel(), CARD_MENU);
        root.add(buildLoginPanel(), CARD_LOGIN);
        root.add(buildRegisterPanel(), CARD_REGISTER);
        root.add(buildPlayerPanel(), CARD_PLAYER);
        root.add(buildStatsPanel(), CARD_STATS);
        root.add(buildLeaderboardPanel(), CARD_LEADERBOARD);
        root.add(buildInstructionsPanel(), CARD_INSTRUCTIONS);
        root.add(buildParentPanel(), CARD_PARENT);
        root.add(buildParentDashboardPanel(), CARD_PARENT_DASH);
        root.add(buildDifficultyPanel(), CARD_DIFFICULTY);
        root.add(gameplayHost, CARD_GAMEPLAY);
        root.add(buildGameOverPanel(), CARD_GAME_OVER);

        getContentPane().setBackground(TypeTrisTheme.SURFACE);
        // Opaque root so CardLayout always paints the active card (opaque=false can hide swaps on some LAFs).
        root.setOpaque(true);
        root.setBackground(TypeTrisTheme.SURFACE);
        gameplayHost.setOpaque(true);
        gameplayHost.setBackground(TypeTrisTheme.SURFACE);

        add(root, BorderLayout.CENTER);
        showCard(CARD_MENU);
        pack();
        setLocationRelativeTo(null);
    }

    /** Shows a card by name and runs light per-screen refresh (e.g. player welcome label). */
    private void showCard(String name) {
        cards.show(root, name);
        if (CARD_PLAYER.equals(name) && loggedInUser != null) {
            playerWelcome.setText("WELCOME, " + loggedInUser);
        }
        if (CARD_DIFFICULTY.equals(name)) {
            refreshDifficultyLocks();
        }
        if (CARD_GAMEPLAY.equals(name) && activeGameplay != null) {
            SwingUtilities.invokeLater(() -> activeGameplay.requestTypingFocus());
        }
    }

    private void goPlayerMenu() {
        setMinimumSize(new Dimension(720, 520));
        if (loggedInUser != null) {
            playerWelcome.setText("WELCOME, " + loggedInUser);
        }
        showCard(CARD_PLAYER);
    }

    private void teardownGameplay() {
        if (activeGameplay != null) {
            activeGameplay.stopTimer();
            activeGameplay = null;
        }
        gameplayHost.removeAll();
        gameplayHost.revalidate();
        gameplayHost.repaint();
    }

    private void mountGameplay() {
        if (loggedInUser == null) {
            return;
        }
        teardownGameplay();
        setMinimumSize(new Dimension(960, 640));
        try {
            GameplayPanel gp =
                    new GameplayPanel(auth, loggedInUser, gameTier, gameSubLevel, () -> {}, this::onGameplayNavigate);
            activeGameplay = gp;
            gameplayHost.add(gp, BorderLayout.CENTER);
            // gameplayHost was empty at first pack(); give it a real size so the gameplay card lays out.
            Dimension gpPref = gp.getPreferredSize();
            gameplayHost.setPreferredSize(new Dimension(Math.max(920, gpPref.width), Math.max(580, gpPref.height)));
            gameplayHost.revalidate();
            showCard(CARD_GAMEPLAY);
            root.revalidate();
            pack();
            validate();
            repaint();
            SwingUtilities.invokeLater(gp::requestTypingFocus);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not start game: " + ex.getMessage(),
                    "Gameplay",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            showCard(CARD_DIFFICULTY);
        }
    }

    /** Routes {@link GameplayPanel} top-bar / game-over navigation. */
    private void onGameplayNavigate(String screen) {
        if (Screens.GAME_OVER.equals(screen)) {
            int s = activeGameplay != null ? activeGameplay.getLastScore() : 0;
            gameOverScore.setText(String.valueOf(s));
            showCard(CARD_GAME_OVER);
            return;
        }
        if (Screens.DIFFICULTY.equals(screen)) {
            teardownGameplay();
            refreshDifficultyLocks();
            showCard(CARD_DIFFICULTY);
            return;
        }
        if (Screens.PLAYER.equals(screen)) {
            teardownGameplay();
            goPlayerMenu();
        }
    }

    private void startGameAt(String tier, int subLevel) {
        if (loggedInUser == null) {
            JOptionPane.showMessageDialog(this, "Log in first.", "Play", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!ClientPrefs.isSubLevelUnlocked(loggedInUser, tier, subLevel)) {
            JOptionPane.showMessageDialog(this, "Clear the previous level in this tier first.", "Locked", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        gameTier = tier;
        gameSubLevel = subLevel;
        mountGameplay();
    }

    private void refreshDifficultyLocks() {
        if (loggedInUser == null) {
            return;
        }
        String[] tiers = { "easy", "medium", "hard" };
        for (int t = 0; t < 3; t++) {
            for (int lv = 1; lv <= 3; lv++) {
                JButton b = diffButtons[t][lv - 1];
                if (b != null) {
                    boolean ok = ClientPrefs.isSubLevelUnlocked(loggedInUser, tiers[t], lv);
                    b.setEnabled(ok);
                    b.setToolTipText(
                            ok
                                    ? "Start " + tiers[t] + " level " + lv
                                    : "Finish level " + (lv - 1) + " in this tier first.");
                }
            }
        }
    }

    /**
     * Wraps each screen like the web layout: soft radial backdrop + bordered surface panel
     * (see {@code index.html} / {@code player-menu.html}).
     */
    private JPanel themedCard(JPanel inner) {
        JPanel shell = new GradientBackdrop();
        inner.setOpaque(true);
        inner.setBackground(TypeTrisTheme.SURFACE_CONTAINER);
        inner.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1),
                BorderFactory.createEmptyBorder(16, 24, 16, 24)));
        shell.add(inner, BorderLayout.CENTER);
        shell.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));
        return shell;
    }

    private void stylePrimaryButton(JButton b) {
        b.setFont(TypeTrisTheme.label(12f, Font.BOLD));
        b.setBackground(TypeTrisTheme.PRIMARY_CONTAINER);
        b.setForeground(TypeTrisTheme.ON_PRIMARY);
        b.setFocusPainted(false);
        b.setBorder(new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
    }

    private void styleSecondaryButton(JButton b) {
        b.setFont(TypeTrisTheme.label(12f, Font.BOLD));
        b.setBackground(TypeTrisTheme.SURFACE_BRIGHT);
        b.setForeground(TypeTrisTheme.ON_SURFACE);
        b.setFocusPainted(false);
        b.setBorder(new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
    }

    private void styleMutedButton(JButton b) {
        b.setFont(TypeTrisTheme.label(11f, Font.PLAIN));
        b.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        b.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        b.setFocusPainted(false);
        b.setBorder(new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
    }

    private void styleTextField(JTextField f) {
        f.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        f.setForeground(TypeTrisTheme.ON_SURFACE);
        f.setCaretColor(TypeTrisTheme.PRIMARY);
        f.setBorder(new LineBorder(TypeTrisTheme.OUTLINE, 1));
        f.setFont(TypeTrisTheme.body(14f, Font.PLAIN));
    }

    private void styleFormLabel(JLabel l) {
        l.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        l.setFont(TypeTrisTheme.label(11f, Font.BOLD));
    }

    private void styleDataTable(JTable t) {
        t.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        t.setForeground(TypeTrisTheme.ON_SURFACE);
        t.setGridColor(TypeTrisTheme.OUTLINE_VARIANT);
        t.setSelectionBackground(TypeTrisTheme.SURFACE_BRIGHT);
        t.setSelectionForeground(TypeTrisTheme.PRIMARY);
        t.setFont(TypeTrisTheme.body(13f, Font.PLAIN));
        t.getTableHeader().setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        t.getTableHeader().setForeground(TypeTrisTheme.PRIMARY);
        t.getTableHeader().setFont(TypeTrisTheme.label(11f, Font.BOLD));
        t.setRowHeight(22);
        t.setFillsViewportHeight(true);
    }

    private JScrollPane themedScroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1));
        sp.getViewport().setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        return sp;
    }

    // main menu for guest

    private JPanel buildMenuPanel() {
        JPanel p = new JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setOpaque(false);

        JLabel title = new JLabel("TYPE-TRIS");
        title.setFont(TypeTrisTheme.headline(36f, Font.BOLD));
        title.setForeground(TypeTrisTheme.PRIMARY);
        title.setAlignmentX(CENTER_ALIGNMENT);
        p.add(title);
        JLabel tag = new JLabel("THE ULTIMATE TYPING CHALLENGE");
        tag.setFont(TypeTrisTheme.label(11f, Font.BOLD));
        tag.setForeground(TypeTrisTheme.SECONDARY);
        tag.setAlignmentX(CENTER_ALIGNMENT);
        p.add(tag);
        p.add(javax.swing.Box.createVerticalStrut(28));

        JButton login = new JButton("LOGIN");
        login.setAlignmentX(CENTER_ALIGNMENT);
        login.setMaximumSize(new Dimension(440, 48));
        stylePrimaryButton(login);
        login.addActionListener(e -> showCard(CARD_LOGIN));
        p.add(login);
        p.add(javax.swing.Box.createVerticalStrut(10));

        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(440, 42));
        JButton hi = new JButton("HIGH SCORES");
        hi.addActionListener(e -> {
            refreshLeaderboard();
            showCard(CARD_LEADERBOARD);
        });
        styleSecondaryButton(hi);
        JButton inst = new JButton("INSTRUCTIONS");
        inst.addActionListener(e -> showCard(CARD_INSTRUCTIONS));
        styleSecondaryButton(inst);
        row.add(hi);
        row.add(inst);
        p.add(row);
        p.add(javax.swing.Box.createVerticalStrut(10));

        JButton reg = new JButton("CREATE ACCOUNT");
        reg.setAlignmentX(CENTER_ALIGNMENT);
        reg.setMaximumSize(new Dimension(440, 40));
        styleSecondaryButton(reg);
        reg.addActionListener(e -> showCard(CARD_REGISTER));
        p.add(reg);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton par = new JButton("PARENT / TEACHER");
        par.setAlignmentX(CENTER_ALIGNMENT);
        par.setMaximumSize(new Dimension(440, 40));
        styleSecondaryButton(par);
        par.setBorder(new LineBorder(TypeTrisTheme.TERTIARY.darker(), 1));
        par.addActionListener(e -> showCard(CARD_PARENT));
        p.add(par);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton exit = new JButton("Exit");
        exit.setAlignmentX(CENTER_ALIGNMENT);
        exit.setMaximumSize(new Dimension(200, 32));
        styleMutedButton(exit);
        exit.addActionListener(e -> System.exit(0));
        p.add(exit);

        return themedCard(p);
    }

    // authentication

    private JPanel buildLoginPanel() {
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        form.setOpaque(false);
        JTextField user = new JTextField(16);
        JPasswordField pass = new JPasswordField(16);
        styleTextField(user);
        styleTextField(pass);
        JLabel lu = new JLabel("Username");
        styleFormLabel(lu);
        JLabel lp = new JLabel("Password");
        styleFormLabel(lp);
        form.add(lu);
        form.add(user);
        form.add(lp);
        form.add(pass);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton ok = new JButton("Login");
        JButton back = new JButton("Back");
        stylePrimaryButton(ok);
        styleSecondaryButton(back);
        ok.addActionListener(e -> {
            String u = user.getText().trim();
            String p = new String(pass.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password are required.", "Login", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                AuthRepository.UserCredentials creds = auth.findByUsername(u);
                if (creds == null || !PasswordUtil.verify(p, creds.salt(), creds.passwordHash())) {
                    JOptionPane.showMessageDialog(this, "Invalid username or password.", "Login", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                auth.recordSessionStart(u);
                loggedInUser = u;
                goPlayerMenu();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Login", JOptionPane.ERROR_MESSAGE);
            }
        });
        back.addActionListener(e -> showCard(CARD_MENU));
        buttons.add(back);
        buttons.add(ok);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        JLabel head = new JLabel("LOGIN");
        head.setFont(TypeTrisTheme.headline(22f, Font.BOLD));
        head.setForeground(TypeTrisTheme.PRIMARY);
        p.add(head, BorderLayout.NORTH);
        p.add(form, BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return themedCard(p);
    }

    private JPanel buildRegisterPanel() {
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        form.setOpaque(false);
        JTextField email = new JTextField(16);
        JTextField user = new JTextField(16);
        JPasswordField pass = new JPasswordField(16);
        JPasswordField confirm = new JPasswordField(16);
        styleTextField(email);
        styleTextField(user);
        styleTextField(pass);
        styleTextField(confirm);
        JLabel e1 = new JLabel("Email");
        styleFormLabel(e1);
        JLabel u1 = new JLabel("Username");
        styleFormLabel(u1);
        JLabel p1 = new JLabel("Password (min 8)");
        styleFormLabel(p1);
        JLabel c1 = new JLabel("Confirm password");
        styleFormLabel(c1);
        form.add(e1);
        form.add(email);
        form.add(u1);
        form.add(user);
        form.add(p1);
        form.add(pass);
        form.add(c1);
        form.add(confirm);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton ok = new JButton("Create account");
        JButton back = new JButton("Back");
        stylePrimaryButton(ok);
        styleSecondaryButton(back);
        ok.addActionListener(e -> {
            String em = email.getText().trim();
            String u = user.getText().trim();
            String p = new String(pass.getPassword());
            String c = new String(confirm.getPassword());
            if (em.isEmpty() || u.isEmpty() || p.isEmpty() || c.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields are required.", "Register", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!p.equals(c)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.", "Register", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (p.length() < 8) {
                JOptionPane.showMessageDialog(this, "Password must be at least 8 characters.", "Register", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String salt = PasswordUtil.newSaltBase64();
            String hash = PasswordUtil.hashBase64(p, salt);
            try {
                AuthRepository.CreateUserResult r = auth.createUser(em, u, hash, salt);
                switch (r) {
                    case CREATED -> JOptionPane.showMessageDialog(this, "Account created. You can log in.", "Register", JOptionPane.INFORMATION_MESSAGE);
                    case EMAIL_TAKEN -> {
                        JOptionPane.showMessageDialog(this, "Email is already taken.", "Register", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    case USERNAME_TAKEN -> {
                        JOptionPane.showMessageDialog(this, "Username is already taken.", "Register", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    case DUPLICATE_UNKNOWN -> {
                        JOptionPane.showMessageDialog(this, "Username or email is already taken.", "Register", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
                showCard(CARD_LOGIN);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Register", JOptionPane.ERROR_MESSAGE);
            }
        });
        back.addActionListener(e -> showCard(CARD_MENU));
        buttons.add(back);
        buttons.add(ok);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        JLabel head = new JLabel("REGISTER");
        head.setFont(TypeTrisTheme.headline(22f, Font.BOLD));
        head.setForeground(TypeTrisTheme.PRIMARY);
        p.add(head, BorderLayout.NORTH);
        p.add(form, BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return themedCard(p);
    }

    // post-login

    private JPanel buildPlayerPanel() {
        JPanel p = new JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setOpaque(false);
        JLabel pm = new JLabel("PLAYER MENU");
        pm.setFont(TypeTrisTheme.label(11f, Font.BOLD));
        pm.setForeground(TypeTrisTheme.SECONDARY);
        pm.setAlignmentX(CENTER_ALIGNMENT);
        p.add(pm);
        p.add(javax.swing.Box.createVerticalStrut(8));
        playerWelcome.setFont(TypeTrisTheme.headline(20f, Font.BOLD));
        playerWelcome.setForeground(TypeTrisTheme.PRIMARY);
        playerWelcome.setAlignmentX(CENTER_ALIGNMENT);
        p.add(playerWelcome);
        p.add(javax.swing.Box.createVerticalStrut(20));

        JButton game = new JButton("START GAME");
        game.setAlignmentX(CENTER_ALIGNMENT);
        game.setMaximumSize(new Dimension(400, 48));
        stylePrimaryButton(game);
        game.addActionListener(e -> {
            if (loggedInUser == null) {
                JOptionPane.showMessageDialog(this, "Log in first.", "Play", JOptionPane.WARNING_MESSAGE);
                return;
            }
            refreshDifficultyLocks();
            showCard(CARD_DIFFICULTY);
        });
        p.add(game);
        p.add(javax.swing.Box.createVerticalStrut(10));

        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(400, 40));
        JButton st = new JButton("STATISTICS");
        st.addActionListener(e -> {
            refreshStats();
            showCard(CARD_STATS);
        });
        styleSecondaryButton(st);
        JButton lb = new JButton("LEADERBOARD");
        lb.addActionListener(e -> {
            refreshLeaderboard();
            showCard(CARD_LEADERBOARD);
        });
        styleSecondaryButton(lb);
        row.add(st);
        row.add(lb);
        p.add(row);
        p.add(javax.swing.Box.createVerticalStrut(10));

        JButton out = new JButton("LOGOUT");
        out.setAlignmentX(CENTER_ALIGNMENT);
        out.setMaximumSize(new Dimension(400, 38));
        styleMutedButton(out);
        out.addActionListener(e -> {
            loggedInUser = null;
            showCard(CARD_MENU);
        });
        p.add(out);

        return themedCard(p);
    }

    // stats and leaderboard data loaders

    private void refreshStats() {
        if (loggedInUser == null) {
            return;
        }
        try {
            PlayerAccount pa = auth.findPlayerAccount(loggedInUser);
            if (pa == null) {
                statsHigh.setText("—");
                statsWpm.setText("—");
                statsAcc.setText("—");
                return;
            }
            statsHigh.setText(String.valueOf(pa.highScore()));
            statsWpm.setText(String.valueOf(pa.bestWPM()));
            statsAcc.setText(String.format("%.1f%%", pa.avgAccuracy()));
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Statistics", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshLeaderboard() {
        leaderboardModel.setRowCount(0);
        try {
            Leaderboard board = new Leaderboard(auth);
            List<PlayerAccount> top = board.getTopPlayers(25);
            int rank = 1;
            for (PlayerAccount pl : top) {
                leaderboardModel.addRow(new Object[] {
                    rank++,
                    pl.username(),
                    pl.highScore(),
                    pl.bestWPM(),
                    pl.avgAccuracy()
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Leaderboard", JOptionPane.ERROR_MESSAGE);
        }
    }

    // stats and leaderboard panel

    private JPanel buildStatsPanel() {
        JPanel grid = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        grid.setOpaque(false);
        statsHigh.setFont(TypeTrisTheme.headline(22f, Font.BOLD));
        statsHigh.setForeground(TypeTrisTheme.PRIMARY);
        statsWpm.setFont(TypeTrisTheme.headline(22f, Font.BOLD));
        statsWpm.setForeground(TypeTrisTheme.PRIMARY);
        statsAcc.setFont(TypeTrisTheme.headline(22f, Font.BOLD));
        statsAcc.setForeground(TypeTrisTheme.PRIMARY);
        JLabel h1 = new JLabel("High score");
        styleFormLabel(h1);
        JLabel h2 = new JLabel("Best WPM");
        styleFormLabel(h2);
        JLabel h3 = new JLabel("Average accuracy");
        styleFormLabel(h3);
        grid.add(h1);
        grid.add(statsHigh);
        grid.add(h2);
        grid.add(statsWpm);
        grid.add(h3);
        grid.add(statsAcc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton back = new JButton("Back");
        styleSecondaryButton(back);
        back.addActionListener(e -> {
            if (loggedInUser != null) {
                goPlayerMenu();
            } else {
                showCard(CARD_MENU);
            }
        });
        JButton refresh = new JButton("Refresh");
        stylePrimaryButton(refresh);
        refresh.addActionListener(e -> refreshStats());
        buttons.add(refresh);
        buttons.add(back);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        JLabel head = new JLabel("PERSONAL STATISTICS");
        head.setFont(TypeTrisTheme.headline(18f, Font.BOLD));
        head.setForeground(TypeTrisTheme.PRIMARY);
        p.add(head, BorderLayout.NORTH);
        p.add(grid, BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return themedCard(p);
    }

    private JPanel buildLeaderboardPanel() {
        JTable table = new JTable(leaderboardModel);
        styleDataTable(table);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton back = new JButton("Back");
        styleSecondaryButton(back);
        back.addActionListener(e -> {
            if (loggedInUser != null) {
                goPlayerMenu();
            } else {
                showCard(CARD_MENU);
            }
        });
        JButton refresh = new JButton("Refresh");
        stylePrimaryButton(refresh);
        refresh.addActionListener(e -> refreshLeaderboard());
        buttons.add(refresh);
        buttons.add(back);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        JLabel head = new JLabel("LEADERBOARD");
        head.setFont(TypeTrisTheme.headline(18f, Font.BOLD));
        head.setForeground(TypeTrisTheme.PRIMARY);
        p.add(head, BorderLayout.NORTH);
        p.add(themedScroll(table), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return themedCard(p);
    }

    // send help text

    private JPanel buildInstructionsPanel() {
        JTextArea area = new JTextArea(
                "TYPE-TRIS — typing + Tetris-style play.\n\n"
                        + "• Register an account or log in.\n"
                        + "• Use Start game to pick a tier and level, then type words to unlock pieces and clear lines.\n"
                        + "• Parent/teacher tools use PIN 2212 (course demo).\n",
                8,
                40);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        area.setForeground(TypeTrisTheme.ON_SURFACE);
        area.setFont(TypeTrisTheme.body(14f, Font.PLAIN));
        area.setCaretColor(TypeTrisTheme.PRIMARY);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton back = new JButton("Back");
        styleSecondaryButton(back);
        back.addActionListener(e -> showCard(CARD_MENU));
        buttons.add(back);
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        JLabel head = new JLabel("HOW TO PLAY");
        head.setFont(TypeTrisTheme.headline(18f, Font.BOLD));
        head.setForeground(TypeTrisTheme.SECONDARY);
        p.add(head, BorderLayout.NORTH);
        p.add(themedScroll(area), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return themedCard(p);
    }

    // parent teacher tools

    private JPanel buildParentPanel() {
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.setOpaque(false);
        JPasswordField pin = new JPasswordField(12);
        styleTextField(pin);
        pin.setColumns(14);
        JLabel pl = new JLabel("Admin PIN");
        styleFormLabel(pl);
        form.add(pl);
        form.add(pin);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton ok = new JButton("Unlock");
        JButton back = new JButton("Back");
        stylePrimaryButton(ok);
        styleSecondaryButton(back);
        ok.addActionListener(e -> {
            String p = new String(pin.getPassword());
            if (!ADMIN_PIN.equals(p)) {
                JOptionPane.showMessageDialog(this, "Invalid PIN.", "Parent", JOptionPane.ERROR_MESSAGE);
                return;
            }
            refreshParentTable();
            showCard(CARD_PARENT_DASH);
        });
        back.addActionListener(e -> showCard(CARD_MENU));
        buttons.add(back);
        buttons.add(ok);
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        JLabel head = new JLabel("PARENT / TEACHER");
        head.setFont(TypeTrisTheme.headline(18f, Font.BOLD));
        head.setForeground(TypeTrisTheme.TERTIARY);
        p.add(head, BorderLayout.NORTH);
        p.add(form, BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return themedCard(p);
    }

    private void refreshParentTable() {
        parentModel.setRowCount(0);
        try {
            for (AuthRepository.UserAccount ua : auth.listUserAccounts()) {
                parentModel.addRow(new Object[] {
                    ua.username(),
                    ua.email(),
                    ua.sessionCount(),
                    ua.lastSessionAt() != null ? ua.lastSessionAt() : "—"
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Accounts", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel buildParentDashboardPanel() {
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tools.setOpaque(false);
        JButton refresh = new JButton("Refresh list");
        refresh.addActionListener(e -> refreshParentTable());
        styleSecondaryButton(refresh);
        JButton del = new JButton("Delete selected");
        del.addActionListener(e -> deleteSelectedParentUser());
        styleSecondaryButton(del);
        JButton reset = new JButton("Reset password");
        reset.addActionListener(e -> resetSelectedPassword());
        styleSecondaryButton(reset);
        tools.add(refresh);
        tools.add(del);
        tools.add(reset);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton back = new JButton("Back");
        styleSecondaryButton(back);
        back.addActionListener(e -> showCard(CARD_PARENT));
        buttons.add(back);

        styleDataTable(parentTable);

        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        JLabel head = new JLabel("ADMIN ACCOUNTS");
        head.setFont(TypeTrisTheme.headline(16f, Font.BOLD));
        head.setForeground(TypeTrisTheme.PRIMARY);
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(tools, BorderLayout.SOUTH);
        p.add(top, BorderLayout.NORTH);
        p.add(themedScroll(parentTable), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return themedCard(p);
    }

    /** deletes the selected account after PIN confirmation. */
    private void deleteSelectedParentUser() {
        int row = parentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a row first.", "Delete", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String username = (String) parentModel.getValueAt(row, 0);
        String pin = JOptionPane.showInputDialog(this, "Re-enter admin PIN to delete " + username + ":");
        if (pin == null) {
            return;
        }
        if (!ADMIN_PIN.equals(pin.trim())) {
            JOptionPane.showMessageDialog(this, "Invalid PIN.", "Delete", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            if (auth.deleteUserByUsername(username)) {
                JOptionPane.showMessageDialog(this, "Deleted " + username + ".");
                refreshParentTable();
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Delete", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** sets a new password hash for the selected user (admin PIN + new password prompts). */
    private void resetSelectedPassword() {
        int row = parentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a row first.", "Reset", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String username = (String) parentModel.getValueAt(row, 0);
        String pin = JOptionPane.showInputDialog(this, "Admin PIN:");
        if (!ADMIN_PIN.equals(pin)) {
            JOptionPane.showMessageDialog(this, "Invalid PIN.", "Reset", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String np = JOptionPane.showInputDialog(this, "New password for " + username + " (min 8 chars):");
        if (np == null || np.length() < 8) {
            JOptionPane.showMessageDialog(this, "Password must be at least 8 characters.", "Reset", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String salt = PasswordUtil.newSaltBase64();
        String hash = PasswordUtil.hashBase64(np, salt);
        try {
            if (auth.resetPasswordByUsername(username, hash, salt)) {
                JOptionPane.showMessageDialog(this, "Password updated for " + username + ".");
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Reset", JOptionPane.ERROR_MESSAGE);
        }
    }

    // difficulty + game over (real gameplay session)

    private JPanel buildDifficultyPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 16));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> goPlayerMenu());
        top.add(back);
        outer.add(top, BorderLayout.NORTH);

        JPanel centerWrap = new JPanel(new BorderLayout(8, 16));
        JLabel heading = new JLabel("SELECT GAME LEVEL");
        heading.setFont(TypeTrisTheme.label(12f, Font.BOLD));
        heading.setForeground(TypeTrisTheme.SECONDARY);
        heading.setHorizontalAlignment(JLabel.CENTER);
        centerWrap.add(heading, BorderLayout.NORTH);
        JPanel tiers = new JPanel(new GridLayout(1, 3, 16, 0));
        tiers.setOpaque(false);
        tiers.add(buildTierColumn("easy", "EASY", TypeTrisTheme.PRIMARY, 0));
        tiers.add(buildTierColumn("medium", "MEDIUM", TypeTrisTheme.YELLOW_MEDIUM, 1));
        tiers.add(buildTierColumn("hard", "HARD", TypeTrisTheme.SECONDARY, 2));
        centerWrap.add(tiers, BorderLayout.CENTER);
        outer.add(centerWrap, BorderLayout.CENTER);

        return themedCard(outer);
    }

    private JPanel buildTierColumn(String tierKey, String title, Color titleColor, int tierIndex) {
        JPanel col = new JPanel();
        col.setLayout(new javax.swing.BoxLayout(col, javax.swing.BoxLayout.Y_AXIS));
        col.setOpaque(true);
        col.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        col.setBorder(BorderFactory.createLineBorder(TypeTrisTheme.OUTLINE_VARIANT));
        JLabel h = new JLabel(title);
        h.setFont(TypeTrisTheme.headline(18f, Font.BOLD));
        h.setForeground(titleColor);
        h.setAlignmentX(CENTER_ALIGNMENT);
        col.add(javax.swing.Box.createVerticalStrut(8));
        col.add(h);
        col.add(javax.swing.Box.createVerticalStrut(16));
        for (int lv = 1; lv <= 3; lv++) {
            int level = lv;
            JButton b = new JButton("Level " + level);
            b.setAlignmentX(CENTER_ALIGNMENT);
            styleSecondaryButton(b);
            b.addActionListener(e -> startGameAt(tierKey, level));
            diffButtons[tierIndex][lv - 1] = b;
            col.add(b);
            col.add(javax.swing.Box.createVerticalStrut(8));
        }
        col.add(javax.swing.Box.createVerticalGlue());
        return col;
    }

    private JPanel buildGameOverPanel() {
        JPanel c = new JPanel();
        c.setLayout(new javax.swing.BoxLayout(c, javax.swing.BoxLayout.Y_AXIS));

        JLabel title = new JLabel("GAME OVER");
        title.setFont(TypeTrisTheme.headline(28f, Font.BOLD));
        title.setForeground(TypeTrisTheme.ON_SURFACE);
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel sub = new JLabel("YOUR FINAL SCORE");
        sub.setFont(TypeTrisTheme.label(10f, Font.BOLD));
        sub.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        sub.setAlignmentX(CENTER_ALIGNMENT);

        gameOverScore.setFont(TypeTrisTheme.headline(36f, Font.BOLD));
        gameOverScore.setForeground(TypeTrisTheme.PRIMARY_CONTAINER);
        gameOverScore.setAlignmentX(CENTER_ALIGNMENT);

        JButton restart = new JButton("Restart level");
        restart.setAlignmentX(CENTER_ALIGNMENT);
        restart.setMaximumSize(new Dimension(320, 44));
        stylePrimaryButton(restart);
        restart.addActionListener(e -> mountGameplay());

        JButton exit = new JButton("Exit to player menu");
        exit.setAlignmentX(CENTER_ALIGNMENT);
        exit.setMaximumSize(new Dimension(320, 40));
        styleSecondaryButton(exit);
        exit.addActionListener(e -> {
            teardownGameplay();
            goPlayerMenu();
        });

        c.add(javax.swing.Box.createVerticalStrut(24));
        c.add(title);
        c.add(javax.swing.Box.createVerticalStrut(16));
        c.add(sub);
        c.add(javax.swing.Box.createVerticalStrut(8));
        c.add(gameOverScore);
        c.add(javax.swing.Box.createVerticalStrut(32));
        c.add(restart);
        c.add(javax.swing.Box.createVerticalStrut(12));
        c.add(exit);
        c.add(javax.swing.Box.createVerticalGlue());

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(c, BorderLayout.CENTER);
        return themedCard(wrap);
    }

    /**
     * Radial wash + soft blobs similar to {@code index.html} background layers.
     */
    private static final class GradientBackdrop extends JPanel {
        GradientBackdrop() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            float cx = w * 0.5f;
            float cy = h * 0.42f;
            float radius = Math.max(w, h) * 0.92f;
            RadialGradientPaint rg = new RadialGradientPaint(
                    cx,
                    cy,
                    radius,
                    new float[] { 0f, 0.42f, 1f },
                    new Color[] {
                        new Color(0x28, 0x26, 0x34),
                        TypeTrisTheme.SURFACE_CONTAINER,
                        TypeTrisTheme.SURFACE
                    });
            g2.setPaint(rg);
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(0xff, 0x51, 0xfa, 30));
            g2.fillOval(w * 3 / 4, -h / 10, w / 2, h / 3);
            g2.setColor(new Color(0xc1, 0xff, 0xfe, 25));
            g2.fillOval(-w / 5, h * 3 / 5, w / 2, h / 2);
            g2.dispose();
        }
    }

    /**
     * shows the main window on the Swing event dispatch thread.
     */
    public static void launch(AuthRepository auth) {
        SwingUtilities.invokeLater(() -> new UserInterface(auth).setVisible(true));
    }
}
