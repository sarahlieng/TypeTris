package typetris.ui;

import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

import typetris.backend.AuthRepository;
import typetris.backend.Leaderboard;
import typetris.backend.PasswordUtil;
import typetris.backend.PlayerAccount;
import typetris.gameplay.GameplayPanel;

/**
 * Desktop shell for Type-tris: one {@link JFrame} with a {@link CardLayout}
 * root.
 * Main menu, combined sign-in / account creation, player hub, statistics,
 * leaderboard,
 * instructions, parental PIN gate and admin tools, and {@link GameplayPanel}
 * after difficulty
 * selection. Data reads and writes go through {@link AuthRepository} (local
 * SQLite).
 */
public final class UserInterface extends JFrame {

    // --- Card names (must match root.add(..., name)) ---

    private static final String CARD_MENU = "menu";
    private static final String CARD_LOGIN = "login";
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

    /**
     * currently authenticated username, or {@code null} on main menu after logout.
     */
    private String loggedInUser;

    private final JLabel playerWelcome = new JLabel(" ");

    // personal statistics (lifetime aggregates)

    private final JLabel statsAvgWpm = new JLabel("—");
    private final JLabel statsPeakWpm = new JLabel("—");
    private final JLabel statsAcc = new JLabel("—");
    private final JLabel statsErrors = new JLabel("—");
    private final JLabel statsPlayTime = new JLabel("—");
    private final JLabel statsHigh = new JLabel("—");
    private final JLabel statsMaxLevel = new JLabel("—");
    private final JLabel statsWords = new JLabel("—");
    private final JLabel statsGames = new JLabel("—");

    private static final String AUTH_VIEW_SIGNIN = "authSignin";
    private static final String AUTH_VIEW_REGISTER = "authRegister";

    /** Swaps sign-in vs create-account on the PLAY card. */
    private JPanel authModePanel;

    private final DefaultTableModel leaderboardModel = new DefaultTableModel(
            new Object[] { "Rank", "Username", "High score", "Best WPM", "Avg accuracy %", "Games played" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final DefaultTableModel parentModel = new DefaultTableModel(
            new Object[] { "Username", "Email", "Sessions", "Last session" }, 0) {
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

    /**
     * Last-started tier / sub-level (1–3); used for restart and new
     * {@link GameplayPanel} instances.
     */
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
        root.add(buildAuthPanel(), CARD_LOGIN);
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
        // Opaque root so CardLayout always paints the active card (opaque=false can
        // hide swaps on some LAFs).
        root.setOpaque(true);
        root.setBackground(TypeTrisTheme.SURFACE);
        gameplayHost.setOpaque(true);
        gameplayHost.setBackground(TypeTrisTheme.SURFACE);

        add(root, BorderLayout.CENTER);
        showCard(CARD_MENU);
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Shows a card by name and runs light per-screen refresh (e.g. player welcome
     * label).
     */
    private void showCard(String name) {
        cards.show(root, name);
        if (CARD_LOGIN.equals(name) && authModePanel != null) {
            ((CardLayout) authModePanel.getLayout()).show(authModePanel, AUTH_VIEW_SIGNIN);
        }
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
        setMinimumSize(new Dimension(960, 700));
        try {
            GameplayPanel gp = new GameplayPanel(auth, loggedInUser, gameTier, gameSubLevel, () -> {
            }, this::onGameplayNavigate);
            activeGameplay = gp;
            gameplayHost.add(gp, BorderLayout.CENTER);
            // gameplayHost was empty at first pack(); give it a real size so the gameplay
            // card lays out.
            Dimension gpPref = gp.getPreferredSize();
            gameplayHost.setPreferredSize(new Dimension(Math.max(920, gpPref.width), Math.max(680, gpPref.height)));
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
            JOptionPane.showMessageDialog(this, "Clear the previous level in this tier first.", "Locked",
                    JOptionPane.INFORMATION_MESSAGE);
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
     * Soft radial backdrop plus bordered surface panel for each full-screen card.
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
        b.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1),
                        BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        ButtonRolloverSupport.install(b, TypeTrisTheme.PRIMARY_CONTAINER);
    }

    /**
     * Outlined control: high contrast on dark panels (menu row buttons, Back,
     * etc.).
     */
    private void styleSecondaryButton(JButton b) {
        b.setFont(TypeTrisTheme.label(12f, Font.BOLD));
        b.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        b.setForeground(TypeTrisTheme.PRIMARY);
        b.setFocusPainted(false);
        b.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.PRIMARY, 2),
                        BorderFactory.createEmptyBorder(9, 18, 9, 18)));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        ButtonRolloverSupport.install(b, TypeTrisTheme.SURFACE_CONTAINER_LOW);
    }

    private void styleMutedButton(JButton b) {
        b.setFont(TypeTrisTheme.label(11f, Font.BOLD));
        b.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        b.setForeground(TypeTrisTheme.ON_SURFACE);
        b.setFocusPainted(false);
        b.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE, 1),
                        BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        ButtonRolloverSupport.install(b, TypeTrisTheme.SURFACE_CONTAINER_HIGH);
    }

    /** “Create account” call-to-action on the sign-in screen. */
    private void styleCreateAccountButton(JButton b) {
        b.setFont(TypeTrisTheme.label(12f, Font.BOLD));
        b.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        b.setForeground(TypeTrisTheme.SECONDARY);
        b.setFocusPainted(false);
        b.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.SECONDARY, 2),
                        BorderFactory.createEmptyBorder(10, 24, 10, 24)));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        ButtonRolloverSupport.install(b, TypeTrisTheme.SURFACE_CONTAINER_HIGH);
    }

    private void styleLinkButton(JButton b) {
        b.setFont(TypeTrisTheme.label(12f, Font.BOLD));
        b.setForeground(TypeTrisTheme.SECONDARY);
        b.setBackground(TypeTrisTheme.SURFACE_CONTAINER);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleTextField(JTextField f) {
        styleTextField(f, false);
    }

    /** @param compact tighter padding and font for login / register rows */
    private void styleTextField(JTextField f, boolean compact) {
        f.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        f.setForeground(TypeTrisTheme.ON_SURFACE);
        f.setCaretColor(TypeTrisTheme.PRIMARY);
        int py = compact ? 3 : 4;
        int px = compact ? 6 : 8;
        f.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE, 1),
                        BorderFactory.createEmptyBorder(py, px, py, px)));
        f.setFont(TypeTrisTheme.body(compact ? 13f : 14f, Font.PLAIN));
    }

    private static final int AUTH_FIELD_WIDTH = 248;

    private static void sizeAuthField(JTextField f) {
        Dimension d = f.getPreferredSize();
        f.setPreferredSize(new Dimension(AUTH_FIELD_WIDTH, d.height));
        f.setMaximumSize(new Dimension(AUTH_FIELD_WIDTH, d.height));
    }

    private static void addAuthFormRow(JPanel form, GridBagConstraints lc, GridBagConstraints fc, int row, JLabel label,
            JTextField field) {
        lc.gridy = row;
        fc.gridy = row;
        form.add(label, lc);
        form.add(field, fc);
    }

    private void styleFormLabel(JLabel l) {
        l.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        l.setFont(TypeTrisTheme.label(11f, Font.BOLD));
    }

    private void addStatRow(JPanel grid, String title, JLabel value) {
        JLabel h = new JLabel(title);
        styleFormLabel(h);
        grid.add(h);
        grid.add(value);
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

    /**
     * Same as {@link #styleDataTable} but removes the focused-cell outline (often a
     * yellow ring on macOS)
     * while keeping row selection fill colors.
     */
    private void styleAdminAccountsTable(JTable t) {
        styleDataTable(t);
        t.setRowHeight(30);
        t.setCellSelectionEnabled(false);
        t.setRowSelectionAllowed(true);
        t.setColumnSelectionAllowed(false);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        DefaultTableCellRenderer noFocusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
                if (c instanceof JComponent jc) {
                    jc.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                }
                return c;
            }
        };
        noFocusRenderer.setOpaque(true);
        t.setDefaultRenderer(Object.class, noFocusRenderer);
        t.setDefaultRenderer(String.class, noFocusRenderer);
        t.setDefaultRenderer(Number.class, noFocusRenderer);

        JTableHeader th = t.getTableHeader();
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table,
                    Object value,
                    boolean isSelected,
                    boolean hasFocus,
                    int row,
                    int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (c instanceof JComponent jc) {
                    jc.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
                    jc.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
                    jc.setOpaque(true);
                }
                if (c instanceof JLabel jl) {
                    jl.setForeground(TypeTrisTheme.PRIMARY);
                    jl.setFont(TypeTrisTheme.label(11f, Font.BOLD));
                    jl.setHorizontalAlignment(SwingConstants.LEADING);
                }
                return c;
            }
        };
        headerRenderer.setOpaque(true);
        th.setDefaultRenderer(headerRenderer);
    }

    /**
     * Username and email get the bulk of width; sessions stays compact. Extra table
     * width is spread
     * across all columns proportionally so “Last session” does not absorb the
     * entire slack.
     */
    private void configureParentAccountsTableColumns(JTable t) {
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        TableColumn c0 = t.getColumnModel().getColumn(0);
        TableColumn c1 = t.getColumnModel().getColumn(1);
        TableColumn c2 = t.getColumnModel().getColumn(2);
        TableColumn c3 = t.getColumnModel().getColumn(3);
        c0.setPreferredWidth(168);
        c1.setPreferredWidth(256);
        c2.setPreferredWidth(94);
        c2.setMinWidth(64);
        c2.setMaxWidth(120);
        c3.setPreferredWidth(188);
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
        p.add(javax.swing.Box.createVerticalStrut(14));
        MenuTetrominoGraphic tetrisT = new MenuTetrominoGraphic();
        tetrisT.setAlignmentX(CENTER_ALIGNMENT);
        p.add(tetrisT);
        p.add(javax.swing.Box.createVerticalStrut(22));

        JButton play = new JButton("LOGIN");
        play.setAlignmentX(CENTER_ALIGNMENT);
        play.setMaximumSize(new Dimension(440, 48));
        stylePrimaryButton(play);
        play.addActionListener(e -> showCard(CARD_LOGIN));
        p.add(play);
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

        JButton par = new JButton("PARENT / TEACHER");
        par.setAlignmentX(CENTER_ALIGNMENT);
        par.setMaximumSize(new Dimension(440, 40));
        styleSecondaryButton(par);
        par.setForeground(TypeTrisTheme.TERTIARY);
        par.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.TERTIARY, 2),
                        BorderFactory.createEmptyBorder(9, 18, 9, 18)));
        par.addActionListener(e -> showCard(CARD_PARENT));
        p.add(par);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton exit = new JButton("Exit");
        exit.setAlignmentX(CENTER_ALIGNMENT);
        exit.setMaximumSize(new Dimension(200, 32));
        styleMutedButton(exit);
        exit.addActionListener(e -> System.exit(0));
        p.add(exit);

        p.add(javax.swing.Box.createVerticalGlue());

        JLabel credits = new JLabel(
                "<html><div style='text-align:center;width:440px;color:#a9abb3;font-size:10px'>"
                        + "<span style='color:#c1fffe'><b>CS2212</b></span> at Western University<br>"
                        + "Winter 2026 · <span style='color:#ff51fa'><b>Group 26</b></span><br><br>"
                        + "Fiona Xu · Wanyi Chen · Jaclyn Guo<br>"
                        + "Sarah Lieng · Bonnie Qiu"
                        + "</div></html>");
        credits.setAlignmentX(CENTER_ALIGNMENT);
        p.add(credits);
        p.add(javax.swing.Box.createVerticalStrut(10));

        return themedCard(p);
    }

    // authentication (sign-in + create account on one card)

    private JPanel buildAuthPanel() {
        authModePanel = new JPanel(new CardLayout());
        authModePanel.setOpaque(false);

        JTextField loginUser = new JTextField();
        JPasswordField loginPass = new JPasswordField();
        styleTextField(loginUser, true);
        styleTextField(loginPass, true);
        sizeAuthField(loginUser);
        sizeAuthField(loginPass);

        JLabel lu = new JLabel("Username");
        styleFormLabel(lu);
        JLabel lp = new JLabel("Password");
        styleFormLabel(lp);

        JPanel loginForm = new JPanel(new GridBagLayout());
        loginForm.setOpaque(false);
        GridBagConstraints lc = new GridBagConstraints();
        lc.insets = new Insets(6, 0, 6, 10);
        lc.anchor = GridBagConstraints.LINE_END;
        lc.gridx = 0;
        lc.fill = GridBagConstraints.NONE;
        lc.weightx = 0;
        GridBagConstraints fc = new GridBagConstraints();
        fc.insets = new Insets(6, 0, 6, 0);
        fc.anchor = GridBagConstraints.LINE_START;
        fc.gridx = 1;
        fc.fill = GridBagConstraints.NONE;
        fc.weightx = 0;
        addAuthFormRow(loginForm, lc, fc, 0, lu, loginUser);
        addAuthFormRow(loginForm, lc, fc, 1, lp, loginPass);

        JPanel loginButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        loginButtons.setOpaque(false);
        JButton loginOk = new JButton("Login");
        JButton loginBack = new JButton("Back");
        stylePrimaryButton(loginOk);
        styleSecondaryButton(loginBack);
        loginOk.addActionListener(e -> {
            String u = loginUser.getText().trim();
            String p = new String(loginPass.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password are required.", "Login",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                AuthRepository.UserCredentials creds = auth.findByUsername(u);
                if (creds == null || !PasswordUtil.verify(p, creds.salt(), creds.passwordHash())) {
                    JOptionPane.showMessageDialog(this, "Invalid username or password.", "Login",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                auth.recordSessionStart(u);
                loggedInUser = u;
                goPlayerMenu();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Login",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        loginBack.addActionListener(e -> showCard(CARD_MENU));
        loginButtons.add(loginBack);
        loginButtons.add(loginOk);

        JPanel loginFormHost = new JPanel(new BorderLayout());
        loginFormHost.setOpaque(false);
        loginFormHost.add(loginForm, BorderLayout.NORTH);

        JLabel noAccountHint = new JLabel("Don't have an account yet?");
        noAccountHint.setFont(TypeTrisTheme.label(11f, Font.PLAIN));
        noAccountHint.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        noAccountHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton createAccountBtn = new JButton("Create account");
        styleCreateAccountButton(createAccountBtn);
        createAccountBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        createAccountBtn.setMaximumSize(new Dimension(300, 46));
        createAccountBtn.addActionListener(
                e -> ((CardLayout) authModePanel.getLayout()).show(authModePanel, AUTH_VIEW_REGISTER));

        JPanel loginCenter = new JPanel();
        loginCenter.setLayout(new javax.swing.BoxLayout(loginCenter, javax.swing.BoxLayout.Y_AXIS));
        loginCenter.setOpaque(false);
        loginCenter.add(loginFormHost);
        loginCenter.add(javax.swing.Box.createVerticalStrut(20));
        loginCenter.add(noAccountHint);
        loginCenter.add(javax.swing.Box.createVerticalStrut(10));
        loginCenter.add(createAccountBtn);

        JPanel loginTab = new JPanel(new BorderLayout(0, 12));
        loginTab.setOpaque(false);
        loginTab.add(loginCenter, BorderLayout.CENTER);
        loginTab.add(loginButtons, BorderLayout.SOUTH);
        loginTab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JTextField email = new JTextField();
        JTextField regUser = new JTextField();
        JPasswordField regPass = new JPasswordField();
        JPasswordField regConfirm = new JPasswordField();
        styleTextField(email, true);
        styleTextField(regUser, true);
        styleTextField(regPass, true);
        styleTextField(regConfirm, true);
        sizeAuthField(email);
        sizeAuthField(regUser);
        sizeAuthField(regPass);
        sizeAuthField(regConfirm);

        JLabel e1 = new JLabel("Email");
        styleFormLabel(e1);
        JLabel u1 = new JLabel("Username");
        styleFormLabel(u1);
        JLabel p1 = new JLabel("Password (min 8)");
        styleFormLabel(p1);
        JLabel c1 = new JLabel("Confirm password");
        styleFormLabel(c1);

        JPanel regForm = new JPanel(new GridBagLayout());
        regForm.setOpaque(false);
        GridBagConstraints rlc = new GridBagConstraints();
        rlc.insets = new Insets(5, 0, 5, 10);
        rlc.anchor = GridBagConstraints.LINE_END;
        rlc.gridx = 0;
        rlc.fill = GridBagConstraints.NONE;
        rlc.weightx = 0;
        GridBagConstraints rfc = new GridBagConstraints();
        rfc.insets = new Insets(5, 0, 5, 0);
        rfc.anchor = GridBagConstraints.LINE_START;
        rfc.gridx = 1;
        rfc.fill = GridBagConstraints.NONE;
        rfc.weightx = 0;
        addAuthFormRow(regForm, rlc, rfc, 0, e1, email);
        addAuthFormRow(regForm, rlc, rfc, 1, u1, regUser);
        addAuthFormRow(regForm, rlc, rfc, 2, p1, regPass);
        addAuthFormRow(regForm, rlc, rfc, 3, c1, regConfirm);

        JPanel regButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        regButtons.setOpaque(false);
        JButton regOk = new JButton("Create account");
        JButton regBack = new JButton("Back");
        stylePrimaryButton(regOk);
        styleSecondaryButton(regBack);
        regOk.addActionListener(e -> {
            String em = email.getText().trim();
            String u = regUser.getText().trim();
            String p = new String(regPass.getPassword());
            String c = new String(regConfirm.getPassword());
            if (em.isEmpty() || u.isEmpty() || p.isEmpty() || c.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields are required.", "Register",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!p.equals(c)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.", "Register", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (p.length() < 8) {
                JOptionPane.showMessageDialog(this, "Password must be at least 8 characters.", "Register",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            String salt = PasswordUtil.newSaltBase64();
            String hash = PasswordUtil.hashBase64(p, salt);
            try {
                AuthRepository.CreateUserResult r = auth.createUser(em, u, hash, salt);
                switch (r) {
                    case CREATED -> {
                        JOptionPane.showMessageDialog(
                                this, "Account created. Sign in with your new username.", "Register",
                                JOptionPane.INFORMATION_MESSAGE);
                        email.setText("");
                        regUser.setText("");
                        regPass.setText("");
                        regConfirm.setText("");
                        loginUser.setText(u);
                        loginPass.setText("");
                        ((CardLayout) authModePanel.getLayout()).show(authModePanel, AUTH_VIEW_SIGNIN);
                        loginPass.requestFocusInWindow();
                    }
                    case EMAIL_TAKEN -> JOptionPane.showMessageDialog(this, "Email is already taken.", "Register",
                            JOptionPane.ERROR_MESSAGE);
                    case USERNAME_TAKEN -> JOptionPane.showMessageDialog(this, "Username is already taken.", "Register",
                            JOptionPane.ERROR_MESSAGE);
                    case DUPLICATE_UNKNOWN -> JOptionPane.showMessageDialog(
                            this, "Username or email is already taken.", "Register", JOptionPane.ERROR_MESSAGE);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Register",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        regBack.addActionListener(e -> showCard(CARD_MENU));
        regButtons.add(regBack);
        regButtons.add(regOk);

        JPanel regHeader = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        regHeader.setOpaque(false);
        JButton backToSignIn = new JButton("Already have an account? Sign in");
        styleLinkButton(backToSignIn);
        backToSignIn.addActionListener(
                e -> ((CardLayout) authModePanel.getLayout()).show(authModePanel, AUTH_VIEW_SIGNIN));
        regHeader.add(backToSignIn);

        JPanel registerTab = new JPanel(new BorderLayout(0, 12));
        registerTab.setOpaque(false);
        JPanel regFormHost = new JPanel(new BorderLayout());
        regFormHost.setOpaque(false);
        regFormHost.add(regForm, BorderLayout.NORTH);
        registerTab.add(regHeader, BorderLayout.NORTH);
        registerTab.add(regFormHost, BorderLayout.CENTER);
        registerTab.add(regButtons, BorderLayout.SOUTH);
        registerTab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        authModePanel.add(loginTab, AUTH_VIEW_SIGNIN);
        authModePanel.add(registerTab, AUTH_VIEW_REGISTER);

        JPanel shell = new JPanel(new BorderLayout(0, 12));
        shell.setOpaque(false);
        JLabel head = new JLabel("PLAY");
        head.setFont(TypeTrisTheme.headline(22f, Font.BOLD));
        head.setForeground(TypeTrisTheme.PRIMARY);
        shell.add(head, BorderLayout.NORTH);
        shell.add(authModePanel, BorderLayout.CENTER);
        return themedCard(shell);
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
                statsAvgWpm.setText("—");
                statsPeakWpm.setText("—");
                statsAcc.setText("—");
                statsErrors.setText("—");
                statsPlayTime.setText("—");
                statsHigh.setText("—");
                statsMaxLevel.setText("—");
                statsWords.setText("—");
                statsGames.setText("—");
                return;
            }
            statsAvgWpm.setText(String.format("%.1f", pa.avgWpm()));
            statsPeakWpm.setText(String.valueOf(pa.bestWPM()));
            statsAcc.setText(String.format("%.1f%%", pa.avgAccuracy()));
            statsErrors.setText(String.valueOf(pa.totalErrors()));
            statsPlayTime.setText(PlayerAccount.formatPlayTime(pa.totalPlayTimeMs()));
            statsHigh.setText(String.valueOf(pa.highScore()));
            statsMaxLevel.setText(String.valueOf(pa.maxLevel()));
            statsWords.setText(String.valueOf(pa.wordsTyped()));
            statsGames.setText(String.valueOf(pa.gamesPlayed()));
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
                        pl.avgAccuracy(),
                        pl.gamesPlayed()
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Leaderboard", JOptionPane.ERROR_MESSAGE);
        }
    }

    // stats and leaderboard panel

    private JPanel buildStatsPanel() {
        JPanel grid = new JPanel(new java.awt.GridLayout(0, 2, 8, 6));
        grid.setOpaque(false);
        for (JLabel v : new JLabel[] {
                statsAvgWpm,
                statsPeakWpm,
                statsAcc,
                statsErrors,
                statsPlayTime,
                statsHigh,
                statsMaxLevel,
                statsWords,
                statsGames
        }) {
            v.setFont(TypeTrisTheme.headline(18f, Font.BOLD));
            v.setForeground(TypeTrisTheme.PRIMARY);
        }
        addStatRow(grid, "Average WPM", statsAvgWpm);
        addStatRow(grid, "Peak WPM", statsPeakWpm);
        addStatRow(grid, "Accuracy %", statsAcc);
        addStatRow(grid, "Error count", statsErrors);
        addStatRow(grid, "Total time played", statsPlayTime);
        addStatRow(grid, "High score", statsHigh);
        addStatRow(grid, "Highest level", statsMaxLevel);
        addStatRow(grid, "Words typed", statsWords);
        addStatRow(grid, "Games played", statsGames);

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
        JPanel wizard = InstructionsScreens.createWizard(() -> showCard(CARD_MENU));
        wizard.setOpaque(false);
        return themedCard(wizard);
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

    /** @return {@code true} if rows were loaded from the database without error */
    private boolean refreshParentTable() {
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
            return true;
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Accounts", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private JPanel buildParentDashboardPanel() {
        JButton refresh = new JButton("Refresh list");
        styleSecondaryButton(refresh);
        JButton viewStats = new JButton("View stats");
        viewStats.addActionListener(e -> viewStatsForSelectedPlayer());
        styleSecondaryButton(viewStats);
        JButton del = new JButton("Delete selected");
        del.addActionListener(e -> deleteSelectedParentUser());
        styleSecondaryButton(del);
        JButton reset = new JButton("Reset password");
        reset.addActionListener(e -> resetSelectedPassword());
        styleSecondaryButton(reset);

        JLabel refreshHint = new JLabel(" ");
        refreshHint.setFont(TypeTrisTheme.label(10f, Font.PLAIN));
        refreshHint.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        refresh.addActionListener(e -> {
            if (refreshParentTable()) {
                int n = parentModel.getRowCount();
                refreshHint.setText(n == 1 ? "Loaded 1 account." : "Loaded " + n + " accounts.");
                Timer clear = new Timer(3500, ev -> refreshHint.setText(" "));
                clear.setRepeats(false);
                clear.start();
            }
        });

        JPanel toolsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        toolsLeft.setOpaque(false);
        toolsLeft.add(refresh);
        toolsLeft.add(javax.swing.Box.createHorizontalStrut(12));
        toolsLeft.add(refreshHint);
        JPanel toolsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        toolsRight.setOpaque(false);
        toolsRight.add(viewStats);
        toolsRight.add(del);
        toolsRight.add(reset);
        JPanel tools = new JPanel(new BorderLayout(12, 0));
        tools.setOpaque(false);
        tools.add(toolsLeft, BorderLayout.WEST);
        tools.add(toolsRight, BorderLayout.EAST);

        JPanel bottomLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bottomLeft.setOpaque(false);
        JButton createAcct = new JButton("Create new account");
        styleSecondaryButton(createAcct);
        createAcct.addActionListener(e -> adminCreateNewAccount());
        JButton resetGlobalScores = new JButton("Reset global high scores");
        styleSecondaryButton(resetGlobalScores);
        resetGlobalScores.addActionListener(e -> adminResetGlobalHighScores());
        bottomLeft.add(createAcct);
        bottomLeft.add(resetGlobalScores);

        JPanel bottomRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomRight.setOpaque(false);
        JButton back = new JButton("Back");
        styleSecondaryButton(back);
        back.addActionListener(e -> showCard(CARD_PARENT));
        bottomRight.add(back);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        bottom.add(bottomLeft, BorderLayout.WEST);
        bottom.add(bottomRight, BorderLayout.EAST);

        styleAdminAccountsTable(parentTable);
        configureParentAccountsTableColumns(parentTable);

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
        p.add(bottom, BorderLayout.SOUTH);
        return themedCard(p);
    }

    /** Admin-only: create a user from a dialog; stays on the accounts screen. */
    private void adminCreateNewAccount() {
        JTextField email = new JTextField();
        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        styleTextField(email, true);
        styleTextField(user, true);
        styleTextField(pass, true);
        styleTextField(confirm, true);
        sizeAuthField(email);
        sizeAuthField(user);
        sizeAuthField(pass);
        sizeAuthField(confirm);

        JLabel e1 = new JLabel("Email");
        styleFormLabel(e1);
        JLabel u1 = new JLabel("Username");
        styleFormLabel(u1);
        JLabel p1 = new JLabel("Password (min 8)");
        styleFormLabel(p1);
        JLabel c1 = new JLabel("Confirm password");
        styleFormLabel(c1);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints lc = new GridBagConstraints();
        lc.insets = new Insets(5, 0, 5, 10);
        lc.anchor = GridBagConstraints.LINE_END;
        lc.gridx = 0;
        lc.fill = GridBagConstraints.NONE;
        lc.weightx = 0;
        GridBagConstraints fc = new GridBagConstraints();
        fc.insets = new Insets(5, 0, 5, 0);
        fc.anchor = GridBagConstraints.LINE_START;
        fc.gridx = 1;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1;
        addAuthFormRow(form, lc, fc, 0, e1, email);
        addAuthFormRow(form, lc, fc, 1, u1, user);
        addAuthFormRow(form, lc, fc, 2, p1, pass);
        addAuthFormRow(form, lc, fc, 3, c1, confirm);

        JPanel wrap = new JPanel(new BorderLayout(0, 0));
        wrap.setOpaque(false);
        wrap.add(form, BorderLayout.NORTH);

        int opt = JOptionPane.showConfirmDialog(
                this,
                wrap,
                "Create new account",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (opt != JOptionPane.OK_OPTION) {
            return;
        }
        String em = email.getText().trim();
        String u = user.getText().trim();
        String pw = new String(pass.getPassword());
        String c = new String(confirm.getPassword());
        if (em.isEmpty() || u.isEmpty() || pw.isEmpty() || c.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All fields are required.", "Create account",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!pw.equals(c)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.", "Create account",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (pw.length() < 8) {
            JOptionPane.showMessageDialog(this, "Password must be at least 8 characters.", "Create account",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String salt = PasswordUtil.newSaltBase64();
        String hash = PasswordUtil.hashBase64(pw, salt);
        try {
            AuthRepository.CreateUserResult r = auth.createUser(em, u, hash, salt);
            switch (r) {
                case CREATED -> {
                    JOptionPane.showMessageDialog(
                            this, "Account created for " + u + ".", "Create account", JOptionPane.INFORMATION_MESSAGE);
                    refreshParentTable();
                }
                case EMAIL_TAKEN -> JOptionPane.showMessageDialog(
                        this, "Email is already taken.", "Create account", JOptionPane.ERROR_MESSAGE);
                case USERNAME_TAKEN -> JOptionPane.showMessageDialog(
                        this, "Username is already taken.", "Create account", JOptionPane.ERROR_MESSAGE);
                case DUPLICATE_UNKNOWN -> JOptionPane.showMessageDialog(
                        this, "Username or email is already taken.", "Create account", JOptionPane.ERROR_MESSAGE);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Create account",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Admin-only: zero out competitive stats for every user after confirmation and
     * PIN check.
     */
    private void adminResetGlobalHighScores() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "This will reset high scores, best WPM, average accuracy, and games played for ALL players.\n"
                        + "Session history and accounts are kept. This cannot be undone.\n\n"
                        + "Are you sure?",
                "Reset global high scores",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        String pin = JOptionPane.showInputDialog(this, "Enter admin PIN to confirm:");
        if (pin == null) {
            return;
        }
        if (!ADMIN_PIN.equals(pin.trim())) {
            JOptionPane.showMessageDialog(this, "Invalid PIN.", "Reset scores", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            int updated = auth.resetAllUsersGameStats();
            JOptionPane.showMessageDialog(
                    this,
                    "Reset stats for " + updated + " account(s).",
                    "Reset scores",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Reset scores", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Shows rolling stats for the table selection (same fields as the player
     * Statistics screen).
     */
    private void viewStatsForSelectedPlayer() {
        int row = parentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a player first.", "Statistics", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String username = (String) parentModel.getValueAt(row, 0);
        try {
            PlayerAccount pa = auth.findPlayerAccount(username);
            if (pa == null) {
                JOptionPane.showMessageDialog(
                        this,
                        "No statistics on file for " + username + ".",
                        "Statistics",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String msg = String.format(
                    """
                            Player: %s

                            Average WPM: %.1f
                            Peak WPM: %d
                            Accuracy: %.1f%%
                            Errors: %d
                            Total time: %s
                            High score: %d
                            Highest level: %d
                            Words typed: %d
                            Games played: %d""",
                    pa.username(),
                    pa.avgWpm(),
                    pa.bestWPM(),
                    pa.avgAccuracy(),
                    pa.totalErrors(),
                    PlayerAccount.formatPlayTime(pa.totalPlayTimeMs()),
                    pa.highScore(),
                    pa.maxLevel(),
                    pa.wordsTyped(),
                    pa.gamesPlayed());
            JOptionPane.showMessageDialog(this, msg, "Player statistics", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Statistics", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** deletes the selected account after PIN confirmation. */
    private void deleteSelectedParentUser() {
        int row = parentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a player first.", "Delete", JOptionPane.WARNING_MESSAGE);
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

    /**
     * sets a new password hash for the selected user (admin PIN + new password
     * prompts).
     */
    private void resetSelectedPassword() {
        int row = parentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a player first.", "Reset", JOptionPane.WARNING_MESSAGE);
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
            JOptionPane.showMessageDialog(this, "Password must be at least 8 characters.", "Reset",
                    JOptionPane.WARNING_MESSAGE);
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
        styleSecondaryButton(back);
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

        JLabel progressSaved = new JLabel("Your progress has been saved");
        progressSaved.setFont(TypeTrisTheme.label(12f, Font.PLAIN));
        progressSaved.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        progressSaved.setAlignmentX(CENTER_ALIGNMENT);

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
        c.add(javax.swing.Box.createVerticalStrut(12));
        c.add(progressSaved);
        c.add(javax.swing.Box.createVerticalStrut(20));
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
     * Classic T-tetromino (three blocks on top, stem down) as a simple branded graphic for the main menu.
     */
    private static final class MenuTetrominoGraphic extends JPanel {
        private static final int CELL = 20;
        private static final int GAP = 3;

        MenuTetrominoGraphic() {
            setOpaque(false);
            int w = 3 * CELL + 2 * GAP;
            int h = 2 * CELL + GAP;
            Dimension d = new Dimension(w, h);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawCell(g2, 0, 0);
            drawCell(g2, 1, 0);
            drawCell(g2, 2, 0);
            drawCell(g2, 1, 1);
            g2.dispose();
        }

        private void drawCell(Graphics2D g2, int col, int row) {
            int x = col * (CELL + GAP);
            int y = row * (CELL + GAP);
            g2.setColor(TypeTrisTheme.PRIMARY_CONTAINER);
            g2.fillRoundRect(x, y, CELL, CELL, 5, 5);
            g2.setColor(TypeTrisTheme.PRIMARY);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(x + 1, y + 1, CELL - 2, CELL - 2, 4, 4);
        }
    }

    /**
     * Radial wash + soft accent blobs behind each screen card.
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
