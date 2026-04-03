package typetris.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
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

        add(root, BorderLayout.CENTER);
        showCard(CARD_MENU);
        pack();
        setLocationRelativeTo(null);
    }

    /** Shows a card by name and runs light per-screen refresh (e.g. player welcome label). */
    private void showCard(String name) {
        cards.show(root, name);
        if (CARD_PLAYER.equals(name) && loggedInUser != null) {
            playerWelcome.setText("Logged in as: " + loggedInUser);
        }
    }

    private void goPlayerMenu() {
        if (loggedInUser != null) {
            playerWelcome.setText("Logged in as: " + loggedInUser);
        }
        showCard(CARD_PLAYER);
    }

    /** consistent outer insets for every full-screen card body. */
    private JPanel padded(JPanel inner) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    // main menu for guest

    private JPanel buildMenuPanel() {
        JPanel p = new JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        JLabel title = new JLabel("TYPE-TRIS");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        title.setAlignmentX(CENTER_ALIGNMENT);
        p.add(title);
        p.add(javax.swing.Box.createVerticalStrut(24));

        JButton login = new JButton("Login");
        login.setAlignmentX(CENTER_ALIGNMENT);
        login.addActionListener(e -> showCard(CARD_LOGIN));
        p.add(login);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton reg = new JButton("Create account");
        reg.setAlignmentX(CENTER_ALIGNMENT);
        reg.addActionListener(e -> showCard(CARD_REGISTER));
        p.add(reg);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton inst = new JButton("Instructions");
        inst.setAlignmentX(CENTER_ALIGNMENT);
        inst.addActionListener(e -> showCard(CARD_INSTRUCTIONS));
        p.add(inst);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton hi = new JButton("Leaderboard");
        hi.setAlignmentX(CENTER_ALIGNMENT);
        hi.addActionListener(e -> {
            refreshLeaderboard();
            showCard(CARD_LEADERBOARD);
        });
        p.add(hi);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton par = new JButton("Parent / teacher");
        par.setAlignmentX(CENTER_ALIGNMENT);
        par.addActionListener(e -> showCard(CARD_PARENT));
        p.add(par);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton exit = new JButton("Exit");
        exit.setAlignmentX(CENTER_ALIGNMENT);
        exit.addActionListener(e -> System.exit(0));
        p.add(exit);

        return padded(p);
    }

    // --- Authentication ---

    private JPanel buildLoginPanel() {
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        JTextField user = new JTextField(16);
        JPasswordField pass = new JPasswordField(16);
        form.add(new JLabel("Username"));
        form.add(user);
        form.add(new JLabel("Password"));
        form.add(pass);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("Login");
        JButton back = new JButton("Back");
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
        p.add(form, BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return padded(p);
    }

    private JPanel buildRegisterPanel() {
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        JTextField email = new JTextField(16);
        JTextField user = new JTextField(16);
        JPasswordField pass = new JPasswordField(16);
        JPasswordField confirm = new JPasswordField(16);
        form.add(new JLabel("Email"));
        form.add(email);
        form.add(new JLabel("Username"));
        form.add(user);
        form.add(new JLabel("Password (min 8)"));
        form.add(pass);
        form.add(new JLabel("Confirm password"));
        form.add(confirm);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("Create account");
        JButton back = new JButton("Back");
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
        p.add(form, BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return padded(p);
    }

    // post-login

    private JPanel buildPlayerPanel() {
        JPanel p = new JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        playerWelcome.setFont(playerWelcome.getFont().deriveFont(Font.BOLD, 18f));
        playerWelcome.setAlignmentX(CENTER_ALIGNMENT);
        p.add(playerWelcome);
        p.add(javax.swing.Box.createVerticalStrut(16));

        JButton game = new JButton("Play (demo)");
        game.setAlignmentX(CENTER_ALIGNMENT);
        game.addActionListener(e -> showCard(CARD_GAME));
        p.add(game);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton st = new JButton("My statistics");
        st.setAlignmentX(CENTER_ALIGNMENT);
        st.addActionListener(e -> {
            refreshStats();
            showCard(CARD_STATS);
        });
        p.add(st);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton lb = new JButton("Leaderboard");
        lb.setAlignmentX(CENTER_ALIGNMENT);
        lb.addActionListener(e -> {
            refreshLeaderboard();
            showCard(CARD_LEADERBOARD);
        });
        p.add(lb);
        p.add(javax.swing.Box.createVerticalStrut(8));

        JButton out = new JButton("Logout");
        out.setAlignmentX(CENTER_ALIGNMENT);
        out.addActionListener(e -> {
            loggedInUser = null;
            showCard(CARD_MENU);
        });
        p.add(out);

        return padded(p);
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
        grid.add(new JLabel("High score"));
        grid.add(statsHigh);
        grid.add(new JLabel("Best WPM"));
        grid.add(statsWpm);
        grid.add(new JLabel("Average accuracy"));
        grid.add(statsAcc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> {
            if (loggedInUser != null) {
                goPlayerMenu();
            } else {
                showCard(CARD_MENU);
            }
        });
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshStats());
        buttons.add(refresh);
        buttons.add(back);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.add(grid, BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return padded(p);
    }

    private JPanel buildLeaderboardPanel() {
        JTable table = new JTable(leaderboardModel);
        table.setFillsViewportHeight(true);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> {
            if (loggedInUser != null) {
                goPlayerMenu();
            } else {
                showCard(CARD_MENU);
            }
        });
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshLeaderboard());
        buttons.add(refresh);
        buttons.add(back);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return padded(p);
    }

    // send help text

    private JPanel buildInstructionsPanel() {
        JTextArea area = new JTextArea(
                "TYPE-TRIS — typing + Tetris-style play.\n\n"
                        + "• Register an account or log in.\n"
                        + "• Use Play (demo) to open the gameplay screen; you can record a sample score to test the database.\n"
                        + "• Parent/teacher tools use PIN 2212 (course demo).\n",
                8,
                40);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> showCard(CARD_MENU));
        buttons.add(back);
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return padded(p);
    }

    // parent teacher tools

    private JPanel buildParentPanel() {
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPasswordField pin = new JPasswordField(12);
        form.add(new JLabel("Admin PIN"));
        form.add(pin);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("Unlock");
        JButton back = new JButton("Back");
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
        p.add(form, BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return padded(p);
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
        JButton refresh = new JButton("Refresh list");
        refresh.addActionListener(e -> refreshParentTable());
        JButton del = new JButton("Delete selected");
        del.addActionListener(e -> deleteSelectedParentUser());
        JButton reset = new JButton("Reset password");
        reset.addActionListener(e -> resetSelectedPassword());
        tools.add(refresh);
        tools.add(del);
        tools.add(reset);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> showCard(CARD_PARENT));
        buttons.add(back);

        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(tools, BorderLayout.NORTH);
        p.add(new JScrollPane(parentTable), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return padded(p);
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

    // gameplay demo

    private JPanel buildGamePanel() {
        JTextArea info = new JTextArea(
                "Gameplay UI in the browser used HTML/JS. This desktop build provides a demo hook into the same SQLite stats.\n\n"
                        + "Use the button below to record a sample game result for your logged-in user (updates high score / WPM / accuracy rolling average).",
                6,
                44);
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton demo = new JButton("Record demo result");
        demo.addActionListener(e -> {
            if (loggedInUser == null) {
                JOptionPane.showMessageDialog(this, "Not logged in.", "Game", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                auth.recordGameResult(loggedInUser, 120, 48, 92.0);
                JOptionPane.showMessageDialog(this, "Saved a demo result for " + loggedInUser + ".", "Game", JOptionPane.INFORMATION_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Game", JOptionPane.ERROR_MESSAGE);
            }
        });
        JButton back = new JButton("Back to menu");
        back.addActionListener(e -> goPlayerMenu());
        buttons.add(demo);
        buttons.add(back);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.add(new JScrollPane(info), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return padded(p);
    }

    /**
     * shows the main window on the Swing event dispatch thread.
     */
    public static void launch(AuthRepository auth) {
        SwingUtilities.invokeLater(() -> new UserInterface(auth).setVisible(true));
    }
}
