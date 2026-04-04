package typetris.gameplay;
 
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
 
import typetris.backend.AuthRepository;
import typetris.backend.CompletedRunStats;
import typetris.backend.PlayerAccount;
import typetris.ui.ButtonRolloverSupport;
import typetris.ui.ClientPrefs;
import typetris.ui.Screens;
import typetris.ui.TypeTrisTheme;
import typetris.ui.UiButtonStyles;
 
/**
 * Swing gameplay screen: left HUD, grid canvas, typing row, top action bar, and pause overlay.
 * <p>
 * Navigation uses {@link typetris.ui.Screens} card names via {@code navigateReplaceCard} (e.g. game over,
 * quit to player menu). Persists bests / unlocks through {@link typetris.ui.ClientPrefs} and
* posts scores through {@link typetris.backend.AuthRepository#recordGameResult}.
 */
public final class GameplayPanel extends JPanel {

    /** Grid cell size in pixels (must match {@link BoardCanvas} drawing). */
    private static final int BOARD_CELL_PX = 28;

    /** High-contrast label on neon power-up chips (cyan / green / pink). */
    private static final Color POWER_CHIP_TEXT = new Color(0x12141a);

    /**
     * Fixed size of the layered gameplay band. Tall enough for the full grid ({@link GameplayModel#ROWS}
     * rows × {@link #BOARD_CELL_PX}px), the word hint + typing row, top bar, and panel insets; otherwise
     * {@link BorderLayout} shrinks the board and the bottom rows clip.
     */
    private static final int VIEWPORT_W = 900;
    private static final int VIEWPORT_H = 680;

    private final AuthRepository auth;
    private final String username;
    private final String tier;
    private final int subLevel;
    /** Unused hook reserved for parity with a "return to menu" callback; quit uses {@code navigateReplaceCard}. */
    private final Runnable onQuitToPlayerMenu;
    /** Switches the app shell to another {@link typetris.ui.Screens} card. */
    private final Consumer<String> navigateReplaceCard;
 
    private GameplayModel model;
    private final BoardCanvas board = new BoardCanvas();
    private final JTextField typing = new JTextField();
    private final JLabel hintTyping = new JLabel(" ");
    private final JLabel hudScore = new JLabel("0");
    private final JLabel hudBest = new JLabel("0");
    /** Large left HUD: overall WPM ({@link GameplayModel#wpmOverall()}). */
    private final JLabel hudWpm = new JLabel("0");
    /** Small sub-label under WPM: live segment WPM ({@link GameplayModel#wpmInstant()}). */
    private final JLabel hudWpmOverall = new JLabel("Current: 0 WPM");

    private final JLabel hudStatAvgWpm = new JLabel("0");
    private final JLabel hudStatPeakWpm = new JLabel("0");
    private final JLabel hudStatAcc = new JLabel("100%");
    private final JLabel hudStatErrors = new JLabel("0");
    private final JLabel hudStatTime = new JLabel("0:00");
    private final JLabel hudStatHigh = new JLabel("0");
    private final JLabel hudStatLevel = new JLabel("1");
    private final JLabel hudStatWords = new JLabel("0");
    private final JLabel hudLines = new JLabel("0");
    private final JLabel hudStreak = new JLabel("0");
    private final JLabel hudMult = new JLabel("x1");
    private final JLabel hudAcc = new JLabel("100%");
    private final JLabel hudLevel = new JLabel("1");
    private final JLabel hudLives = new JLabel("\u2665".repeat(GameplayModel.STARTING_LIVES));
    /** Semi-transparent layer over the play area while {@link GameplayModel#isPaused()}. */
    private final JPanel pauseOverlay = new JPanel();
    /** Prevents {@link DocumentListener} recursion when the model rewrites the typing field. */
    private boolean ignoreTypingDoc;

    /**
     * At most one pending {@link #syncTypingFromDocumentAfterNotification()} on the EDT. Without this,
     * each document event (and each programmatic {@code setText}) queues a separate {@code invokeLater},
     * which can backlog the EDT and make typing appear seconds behind.
     */
    private boolean typingSyncScheduled;
 
    /** ~60 FPS game step; drives {@link GameplayModel#tick(double)} and board repaint. */
    private Timer gameTimer;
    /** Snapshot for the game-over screen after {@link GameplayModel.Listener#onGameOver()}. */
    private int lastScore;
 
    /** Last piece word we showed a typing prompt for (refocus typing when a new word spawns). */
    private String lastTypingTargetWord;

    private final GameplayAudio audio = new GameplayAudio();

    private final JLabel powerStatusLabel = new JLabel(" ");
    private JButton powerFreezeBtn;
    private JButton powerEasyBtn;
    private JButton powerBlastBtn;

    private JLayeredPane gameplayLayeredPane;
    private final JLabel lifeLostFlash = new JLabel("", SwingConstants.CENTER);
 
    public GameplayPanel(
            AuthRepository auth,
            String username,
            String tier,
            int subLevel,
            Runnable onQuitToPlayerMenu,
            Consumer<String> navigateReplaceCard) {
        this.auth = auth;
        this.username = username;
        this.tier = tier;
        this.subLevel = subLevel;
        this.onQuitToPlayerMenu = onQuitToPlayerMenu;
        this.navigateReplaceCard = navigateReplaceCard;
 
        setLayout(new BorderLayout(0, 0));
        setBackground(TypeTrisTheme.SURFACE);
        setOpaque(true);
        setFocusable(true);
 
        // Model: tier/subLevel select fall speed and word length; best score seeds HUD "best".
        int best = ClientPrefs.getBestScore(username, tier, subLevel);
        model = new GameplayModel(tier, subLevel, best, new GameplayModel.Listener() {
            @Override
            public void onRepaint() {
                board.repaint();
            }

            @Override
            public void onHudChanged() {
                syncHud();
            }

            @Override
            public void onAudioUserActivity() {
                audio.ensureStarted();
            }

            @Override
            public void onAudioBlockLand() {
                audio.playBlockLand();
            }

            @Override
            public void onAudioRowClear(int clearedCount) {
                audio.playRowClear(clearedCount);
            }

            @Override
            public void onAudioPowerUp() {
                audio.playPowerUp();
            }

            @Override
            public void onAudioTypingError() {
                audio.playTypingError();
            }

            @Override
            public void onLifeLost(int livesRemaining) {
                showLifeLostNotification(livesRemaining);
            }

            @Override
            public void onGameOver() {
                if (model == null) return;
                audio.stopBgm();
                lastScore = model.getScore();
                ClientPrefs.putBestScoreIfHigher(username, tier, subLevel, model.getScore());
                // Clearing a sub-level unlocks the next within the same tier (matches web localStorage).
                ClientPrefs.recordSubLevelClear(username, tier, subLevel);
                try {
                    auth.recordGameResult(
                            username,
                            new CompletedRunStats(
                                    model.getScore(),
                                    model.wpmOverall(),
                                    model.accuracyPercent(),
                                    model.getWrongStrokes(),
                                    model.getSessionActivePlayMs(),
                                    model.getPeakLevelRun(),
                                    model.getWordsTypedRun(),
                                    model.getPeakWpmRun()));
                } catch (Exception ignored) {
                }
                SwingUtilities.invokeLater(() -> navigateReplaceCard.accept(Screens.GAME_OVER));
            }
        });
 
 
        // center HUD and board + typing
        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(16, 16, 16, 16));
 
        JPanel hudCol = buildHudColumn();
        center.add(hudCol, BorderLayout.WEST);
        // CHANGE 3: add right column (LEVEL & PROGRESS + COMBO & STREAK) matching the col-span-3
        // right section in gameplay.html. Previously these metrics lived in buildHudColumn().
        center.add(buildRightColumn(), BorderLayout.EAST);
 
        JPanel playCol = new JPanel(new BorderLayout());
        playCol.setOpaque(false);
        Dimension boardSize =
                new Dimension(GameplayModel.COLS * BOARD_CELL_PX, GameplayModel.ROWS * BOARD_CELL_PX);
        board.setPreferredSize(boardSize);
        board.setMinimumSize(boardSize);
        board.setMaximumSize(boardSize);
        board.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                GameplayPanel.this.requestFocusInWindow();
            }
        });
        JPanel boardWrap = new JPanel(new GridBagLayout());
        boardWrap.setOpaque(false);
        GridBagConstraints bgbc = new GridBagConstraints();
        bgbc.gridx = 0;
        bgbc.gridy = 0;
        bgbc.weightx = 1;
        bgbc.weighty = 1;
        bgbc.anchor = GridBagConstraints.CENTER;
        boardWrap.add(board, bgbc);
        playCol.add(boardWrap, BorderLayout.CENTER);
 
        JPanel belowBoard = new JPanel(new BorderLayout(0, 4));
        belowBoard.setOpaque(false);

        JPanel typingRow = new JPanel(new BorderLayout(8, 4));
        typingRow.setOpaque(false);
        typing.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        typing.setForeground(TypeTrisTheme.ON_SURFACE);
        typing.setCaretColor(TypeTrisTheme.PRIMARY);
        typing.setFont(TypeTrisTheme.body(14f, Font.PLAIN));
        typing.setColumns(14);
        typing.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE, 1),
                        new EmptyBorder(4, 10, 4, 10)));
        typing.setHorizontalAlignment(JTextField.LEFT);
        // Force A–Z only and uppercase so the model always sees a single consistent alphabet.
        ((AbstractDocument) typing.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (text == null) {
                    super.replace(fb, offset, length, text, attrs);
                    return;
                }
                StringBuilder up = new StringBuilder();
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c >= 'a' && c <= 'z') {
                        c = (char) (c - 'a' + 'A');
                    }
                    if (c >= 'A' && c <= 'Z') {
                        up.append(c);
                    }
                }
                super.replace(fb, offset, length, up.toString(), attrs);
            }
        });
        // While the JTextField has focus it consumes arrows/space; web uses blur() after unlock. We transfer
        // focus to this panel and also route arrows/space from the field when unlocked (gameplay.js).
        typing.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (model == null || model.isOver() || model.isPaused()) {
                    return;
                }
                GameplayModel.Piece pc = model.getPiece();
                if (pc == null) {
                    return;
                }
                // Always eat space in this field so it never inserts a character (web prevents default).
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    e.consume();
                    if (pc.unlocked) {
                        model.onSpace();
                        board.repaint();
                    }
                    return;
                }
                if (!pc.unlocked) {
                    return;
                }
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> {
                        e.consume();
                        model.onArrowLeft();
                    }
                    case KeyEvent.VK_RIGHT -> {
                        e.consume();
                        model.onArrowRight();
                    }
                    case KeyEvent.VK_UP -> {
                        e.consume();
                        model.onArrowUp();
                    }
                    case KeyEvent.VK_DOWN -> {
                        e.consume();
                        model.onArrowDown();
                    }
                    default -> {
                    }
                }
                board.repaint();
            }
        });
        // Same pipeline as web "input" + onTypingInput: must not call setText inside document notifications.
        typing.getDocument().addDocumentListener(new DocumentListener() {
            private void scheduleTypingSync() {
                if (ignoreTypingDoc || typingSyncScheduled) {
                    return;
                }
                typingSyncScheduled = true;
                SwingUtilities.invokeLater(() -> {
                    typingSyncScheduled = false;
                    syncTypingFromDocumentAfterNotification();
                });
            }

            private void syncTypingFromDocumentAfterNotification() {
                if (ignoreTypingDoc || model == null || model.isOver() || model.isPaused()) {
                    return;
                }
                GameplayModel.Piece pcBefore = model.getPiece();
                if (pcBefore == null) {
                    return;
                }
                // Web blurs input when unlocked; if focus returns to the field, ignore edits here.
                if (pcBefore.unlocked) {
                    ignoreTypingDoc = true;
                    try {
                        typing.setText("");
                        typing.setCaretPosition(0);
                    } finally {
                        ignoreTypingDoc = false;
                    }
                    return;
                }
                model.onTypingFieldChanged(typing.getText());
                String shown = model.currentTypingDisplay();
                ignoreTypingDoc = true;
                try {
                    // Avoid setText when unchanged — it still fires document events and would re-queue work.
                    if (!shown.contentEquals(typing.getText())) {
                        typing.setText(shown);
                    }
                    typing.setCaretPosition(shown.length());
                } finally {
                    ignoreTypingDoc = false;
                }
                GameplayModel.Piece pcAfter = model.getPiece();
                if (pcAfter != null && pcAfter.unlocked) {
                    SwingUtilities.invokeLater(GameplayPanel.this::requestFocusInWindow);
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleTypingSync();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleTypingSync();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleTypingSync();
            }
        });
        installTypingPowerKeyBindings(typing);
 
        hintTyping.setFont(TypeTrisTheme.label(11f, Font.BOLD));
        hintTyping.setForeground(TypeTrisTheme.PRIMARY);
        hintTyping.setHorizontalAlignment(JLabel.CENTER);
        JPanel typingFieldWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        typingFieldWrap.setOpaque(false);
        typingFieldWrap.add(typing);
        typingRow.add(typingFieldWrap, BorderLayout.CENTER);
        typingRow.add(hintTyping, BorderLayout.SOUTH);
        // CHANGE 4 continued: typingRow goes into belowBoard CENTER; belowBoard goes into playCol SOUTH.
        belowBoard.add(typingRow, BorderLayout.CENTER);
        playCol.add(belowBoard, BorderLayout.SOUTH);
 
        center.add(playCol, BorderLayout.CENTER);
 
        // Top bar: power-ups + pause (slightly separated from playfield like a toolbar)
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(true);
        topBar.setBackground(TypeTrisTheme.SURFACE_CONTAINER);
        topBar.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, TypeTrisTheme.OUTLINE_VARIANT),
                        new EmptyBorder(8, 10, 10, 10)));
        JPanel powerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        powerRow.setOpaque(false);
        powerStatusLabel.setFont(TypeTrisTheme.label(10f, Font.PLAIN));
        powerStatusLabel.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        powerRow.add(powerStatusLabel);
        powerFreezeBtn = compactPowerButton("Freeze (1)", TypeTrisTheme.PRIMARY_CONTAINER, POWER_CHIP_TEXT);
        powerFreezeBtn.setToolTipText("Time freeze — slows drops for a few seconds (matches instructions).");
        powerFreezeBtn.addActionListener(e -> tryActivatePowerup(1));
        powerEasyBtn = compactPowerButton("Easy words (2)", TypeTrisTheme.TERTIARY, POWER_CHIP_TEXT);
        powerEasyBtn.setToolTipText("Easy words — next spawns use shorter words.");
        powerEasyBtn.addActionListener(e -> tryActivatePowerup(2));
        powerBlastBtn = compactPowerButton("Blast (3)", TypeTrisTheme.ERROR, POWER_CHIP_TEXT);
        powerBlastBtn.setToolTipText("Block buster — next lock clears nearby stack cells.");
        powerBlastBtn.addActionListener(e -> tryActivatePowerup(3));
        powerRow.add(powerFreezeBtn);
        powerRow.add(powerEasyBtn);
        powerRow.add(powerBlastBtn);
        topBar.add(powerRow, BorderLayout.WEST);

        JPanel topEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topEast.setOpaque(false);
        JButton pauseBtn = new JButton("Pause");
        UiButtonStyles.applyPrimary(pauseBtn);
        pauseBtn.setToolTipText("Pause or resume (Esc)");
        pauseBtn.addActionListener(e -> {
            model.togglePause();
            pauseOverlay.setVisible(model.isPaused());
            audio.setBgmPaused(model.isPaused());
            if (!model.isPaused()) {
                refocusTypingIfNeeded();
            }
            syncHud();
        });
        topEast.add(pauseBtn);
        topBar.add(topEast, BorderLayout.EAST);
 
        // Stack main layout + pause overlay in one fixed-size band (matches compact desktop layout).
        JLayeredPane layered = new JLayeredPane();
        layered.setPreferredSize(new Dimension(VIEWPORT_W, VIEWPORT_H));
        wrapCenter(layered, topBar, center, pauseOverlay);
        gameplayLayeredPane = layered;
        lifeLostFlash.setFont(TypeTrisTheme.headline(22f, Font.BOLD));
        lifeLostFlash.setForeground(TypeTrisTheme.ERROR);
        lifeLostFlash.setOpaque(false);
        lifeLostFlash.setVisible(false);
        lifeLostFlash.setBounds(VIEWPORT_W / 2 - 170, VIEWPORT_H / 2 - 80, 340, 100);
        layered.add(lifeLostFlash, JLayeredPane.DRAG_LAYER);

        model.startRound(); // start round after UI is built
 
        installKeyboardActions();
        installPlacementKeyListenerOnPanel();
        gameTimer = new Timer(16, e -> {
            model.tick(0.016);
            board.repaint();
            if (model.getTimeFreezeRemainingSec() > 0) {
                syncHud();
            }
        });
        gameTimer.start();

        // Start BGM as soon as gameplay is shown (desktop has no browser autoplay gate).
        audio.ensureStarted();

        // HUD already refreshed by startRound() so fireHud()
 
        // dim overlay + centered card (added after wrapCenter registers the overlay layer)
        pauseOverlay.setLayout(new GridBagLayout());
        pauseOverlay.setBackground(new Color(15, 18, 24, 230));
        pauseOverlay.setOpaque(true);
        pauseOverlay.setVisible(false);

        JPanel pauseCard = new JPanel();
        pauseCard.setLayout(new BoxLayout(pauseCard, BoxLayout.Y_AXIS));
        pauseCard.setOpaque(true);
        pauseCard.setBackground(TypeTrisTheme.SURFACE_CONTAINER);
        pauseCard.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.PRIMARY, 2),
                        new EmptyBorder(28, 44, 32, 44)));

        JLabel pausedHead = new JLabel("PAUSED");
        pausedHead.setFont(TypeTrisTheme.headline(28f, Font.BOLD));
        pausedHead.setForeground(TypeTrisTheme.PRIMARY);
        pausedHead.setAlignmentX(Component.CENTER_ALIGNMENT);
        pauseCard.add(pausedHead);
        pauseCard.add(Box.createVerticalStrut(10));

        JLabel pausedSub = new JLabel("Music is muted until you resume.");
        pausedSub.setFont(TypeTrisTheme.body(12f, Font.PLAIN));
        pausedSub.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        pausedSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        pauseCard.add(pausedSub);
        pauseCard.add(Box.createVerticalStrut(28));

        JButton resume = new JButton("Resume game");
        UiButtonStyles.applyPrimary(resume);
        resume.setAlignmentX(Component.CENTER_ALIGNMENT);
        resume.setMaximumSize(new Dimension(240, 46));
        resume.addActionListener(e -> {
            model.setPaused(false);
            pauseOverlay.setVisible(false);
            audio.setBgmPaused(false);
            refocusTypingIfNeeded();
            syncHud();
        });
        pauseCard.add(resume);
        pauseCard.add(Box.createVerticalStrut(12));

        JButton pauseQuit = new JButton("Quit to menu");
        UiButtonStyles.applySecondary(pauseQuit);
        pauseQuit.setAlignmentX(Component.CENTER_ALIGNMENT);
        pauseQuit.setMaximumSize(new Dimension(240, 42));
        pauseQuit.addActionListener(e -> quitWithUnsavedWarning());
        pauseCard.add(pauseQuit);

        GridBagConstraints og = new GridBagConstraints();
        og.gridx = 0;
        og.gridy = 0;
        og.weightx = 1;
        og.weighty = 1;
        og.anchor = GridBagConstraints.CENTER;
        og.insets = new Insets(0, 0, 0, 0);
        pauseOverlay.add(pauseCard, og);
    }

    private void quitWithUnsavedWarning() {
        JPanel wrap = new JPanel(new BorderLayout(0, 12));
        wrap.setOpaque(false);
        JLabel head = new JLabel("Quit to menu?");
        head.setFont(TypeTrisTheme.headline(18f, Font.BOLD));
        head.setForeground(TypeTrisTheme.ON_SURFACE);
        JTextArea body = new JTextArea(
                "This run won’t update your best score for this level. You can start again anytime from the player menu.");
        body.setEditable(false);
        body.setOpaque(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setColumns(26);
        body.setRows(3);
        body.setFont(TypeTrisTheme.body(12f, Font.PLAIN));
        body.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        body.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        wrap.add(head, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);

        String[] opts = { "Quit to menu", "Keep playing" };
        int r =
                JOptionPane.showOptionDialog(
                        this,
                        wrap,
                        "Quit game",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        opts,
                        opts[1]);
        if (r == 0) {
            navigateReplaceCard.accept(Screens.PLAYER);
        }
    }
 
    /** Places the main stack on the default layer and the pause strip on top. */
    private void wrapCenter(JLayeredPane layered, JPanel topBar, JPanel center, JPanel overlay) {
        JPanel stack = new JPanel(new BorderLayout());
        stack.setOpaque(false);
        stack.add(topBar, BorderLayout.NORTH);
        stack.add(center, BorderLayout.CENTER);
        stack.setBounds(0, 0, VIEWPORT_W, VIEWPORT_H);
        layered.add(stack, JLayeredPane.DEFAULT_LAYER);
        // Pause overlay covers the same band as the stack.
        overlay.setBounds(0, 0, VIEWPORT_W, VIEWPORT_H);
        layered.add(overlay, JLayeredPane.PALETTE_LAYER);
        add(layered, BorderLayout.CENTER);
    }
 
    /** Final score from the last completed run; read by the shell when showing game over. */
    public int getLastScore() {
        return lastScore;
    }
 
    /** Small bordered control matching the neon / high-contrast web buttons. */
    private JButton compactPowerButton(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFont(TypeTrisTheme.label(9f, Font.BOLD));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1),
                        new EmptyBorder(4, 8, 4, 8)));
        ButtonRolloverSupport.install(b, bg);
        return b;
    }

    private void installTypingPowerKeyBindings(JTextField tf) {
        int c = JComponent.WHEN_FOCUSED;
        for (int i = 1; i <= 3; i++) {
            final int slot = i;
            tf.getInputMap(c).put(KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, 0), "tpow" + i);
            tf.getActionMap().put("tpow" + i, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    tryActivatePowerup(slot);
                }
            });
        }
    }

    private void showLifeLostNotification(int livesRemaining) {
        if (gameplayLayeredPane == null) {
            return;
        }
        lifeLostFlash.setText(
                "<html><center>Life lost!<br><span style='font-size:18px'>" + livesRemaining + " left</span></center></html>");
        lifeLostFlash.setVisible(true);
        gameplayLayeredPane.setLayer(lifeLostFlash, JLayeredPane.DRAG_LAYER);
        gameplayLayeredPane.moveToFront(lifeLostFlash);
        Timer hide = new Timer(850, ev -> {
            lifeLostFlash.setVisible(false);
            ((Timer) ev.getSource()).stop();
        });
        hide.setRepeats(false);
        hide.start();
    }

    private void tryActivatePowerup(int slot) {
        if (model == null || model.isOver() || model.isPaused()) {
            return;
        }
        boolean ok =
                switch (slot) {
                    case 1 -> model.activateTimeFreeze();
                    case 2 -> model.activateEasyWords();
                    case 3 -> model.activateBlockBuster();
                    default -> false;
                };
        if (ok) {
            audio.ensureStarted();
            audio.playPowerUp();
            board.repaint();
            syncHud();
        }
    }
 
    private JPanel buildHudColumn() {
        JPanel p = new JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        p.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1),
                        new EmptyBorder(12, 14, 14, 14)));

        p.add(hudBlock("SCORE", hudScore, 28f));
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(hudBlock("BEST SCORE", hudBest, 22f));
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(hudBlock("WORDS PER MINUTE", hudWpm, 24f));
        p.add(Box.createVerticalGlue());

        JPanel bottomHud = new JPanel();
        bottomHud.setLayout(new BoxLayout(bottomHud, BoxLayout.Y_AXIS));
        bottomHud.setOpaque(false);
        bottomHud.setAlignmentX(Component.LEFT_ALIGNMENT);
        hudTiny(hudWpmOverall);
        hudWpmOverall.setHorizontalAlignment(SwingConstants.LEADING);
        bottomHud.add(hudWpmOverall);
        bottomHud.add(Box.createVerticalStrut(10));
        JPanel sessionBlock = buildSessionStatsSection();
        sessionBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomHud.add(sessionBlock);

        p.add(bottomHud);

        return p;
    }

    /** Session metrics in a compact inset card (labels left, values right; snug size, left-aligned in HUD). */
    private JPanel buildSessionStatsSection() {
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel statsHead = new JLabel("This session");
        statsHead.setFont(TypeTrisTheme.label(9f, Font.BOLD));
        statsHead.setForeground(TypeTrisTheme.TERTIARY);
        statsHead.setHorizontalAlignment(SwingConstants.LEADING);
        statsHead.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(statsHead);
        wrap.add(Box.createVerticalStrut(6));

        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(true);
        card.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        card.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1),
                        new EmptyBorder(6, 8, 8, 8)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints cl = new GridBagConstraints();
        cl.gridx = 0;
        cl.anchor = GridBagConstraints.LINE_START;
        cl.insets = new Insets(2, 0, 2, 10);
        cl.weightx = 0;
        cl.weighty = 0;
        GridBagConstraints cv = new GridBagConstraints();
        cv.gridx = 1;
        cv.anchor = GridBagConstraints.LINE_END;
        cv.insets = new Insets(2, 0, 2, 0);
        cv.weightx = 0;
        cv.weighty = 0;
        cv.fill = GridBagConstraints.NONE;

        int row = 0;
        addSessionStatRow(card, cl, cv, row++, "Avg WPM", hudStatAvgWpm);
        addSessionStatRow(card, cl, cv, row++, "Peak WPM", hudStatPeakWpm);
        addSessionStatRow(card, cl, cv, row++, "Accuracy", hudStatAcc);
        addSessionStatRow(card, cl, cv, row++, "Errors", hudStatErrors);
        addSessionStatRow(card, cl, cv, row++, "Time", hudStatTime);
        addSessionStatRow(card, cl, cv, row++, "High score", hudStatHigh);
        addSessionStatRow(card, cl, cv, row++, "Level", hudStatLevel);
        addSessionStatRow(card, cl, cv, row++, "Words typed", hudStatWords);

        Dimension cp = card.getPreferredSize();
        card.setPreferredSize(cp);
        card.setMaximumSize(cp);
        wrap.add(card);

        Dimension wp = wrap.getPreferredSize();
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, wp.height));
        return wrap;
    }

    private static void addSessionStatRow(
            JPanel card, GridBagConstraints cl, GridBagConstraints cv, int row, String title, JLabel value) {
        JLabel t = new JLabel(title);
        t.setFont(TypeTrisTheme.body(10f, Font.PLAIN));
        t.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        value.setFont(TypeTrisTheme.body(12f, Font.BOLD));
        value.setForeground(TypeTrisTheme.PRIMARY);
        cl.gridy = row;
        cv.gridy = row;
        card.add(t, cl);
        card.add(value, cv);
    }
 
    /**
     * Right column: lives, level / lines cleared, then combo &amp; streak (matches gameplay.html right HUD).
     */
    private JPanel buildRightColumn() {
        JPanel p = new JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        p.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1),
                        new EmptyBorder(12, 14, 14, 14)));

        p.add(hudBlock("LIVES", hudLives, 28f));
        p.add(javax.swing.Box.createVerticalStrut(8));

        // LEVEL & PROGRESS section (#hud-current-level, #hud-lines-cleared, #hud-level-progress-fill)
        p.add(hudBlock("CURRENT LEVEL:", hudLevel, 28f));
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(hudBlock("LINES CLEARED:", hudLines, 22f));
        p.add(javax.swing.Box.createVerticalStrut(8));
 
        // COMBO & STREAK section (#hud-streak, #hud-combo-multiplier, #hud-accuracy)
        p.add(hudBlock("LINE STREAK:", hudStreak, 28f));
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(hudBlock("MULTIPLIER:", hudMult, 22f));
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(hudBlock("ACCURACY:", hudAcc, 22f));
 
        return p;
    }
 
    private JPanel hudBlock(String title, JLabel value, float size) {
        JPanel b = new JPanel(new BorderLayout());
        b.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(TypeTrisTheme.label(10f, Font.BOLD));
        t.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        value.setFont(TypeTrisTheme.headline(size, Font.BOLD));
        value.setForeground(TypeTrisTheme.PRIMARY);
        b.add(t, BorderLayout.NORTH);
        b.add(value, BorderLayout.CENTER);
        Dimension d = b.getPreferredSize();
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, d.height));
        return b;
    }
 
    private JLabel hudTiny(JLabel l) {
        l.setFont(TypeTrisTheme.label(11f, Font.PLAIN));
        l.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        l.setHorizontalAlignment(SwingConstants.LEADING);
        Dimension d = l.getPreferredSize();
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, d.height));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
 
    /** Copies all HUD labels and the level bar from the model. */
    private void syncHud() {
        if (model == null) {
            return;
        }
        hudScore.setText(String.valueOf(model.getScore()));
        hudBest.setText(String.valueOf(model.getBestDisplayed()));
        // Large WPM = overall run average; small line = live segment WPM (swapped vs original HTML mapping).
        hudWpm.setText(String.valueOf(model.wpmOverall()));
        hudWpmOverall.setText("Current: " + model.wpmInstant() + " WPM");
        hudStatAvgWpm.setText(String.valueOf(model.wpmOverall()));
        hudStatPeakWpm.setText(String.valueOf(model.getPeakWpmRun()));
        hudStatAcc.setText(model.accuracyPercent() + "%");
        hudStatErrors.setText(String.valueOf(model.getWrongStrokes()));
        hudStatTime.setText(PlayerAccount.formatPlayTime(model.getSessionActivePlayMs()));
        hudStatHigh.setText(String.valueOf(model.getBestDisplayed()));
        hudStatLevel.setText(String.valueOf(model.getPeakLevelRun()));
        hudStatWords.setText(String.valueOf(model.getWordsTypedRun()));
        int g = model.getFlawlessStreakGoal();
        int p = model.getFlawlessStreakProgress();
        int ch = model.getPowerupCharges();
        StringBuilder st = new StringBuilder();
        st.append(String.format("Flawless %d/%d  •  Charges: %d", p, g, ch));
        if (model.getTimeFreezeRemainingSec() > 0.05) {
            st.append(String.format("  •  Freeze %.1fs", model.getTimeFreezeRemainingSec()));
        }
        if (model.getEasyWordSpawnsRemaining() > 0) {
            st.append(String.format("  •  Easy×%d", model.getEasyWordSpawnsRemaining()));
        }
        if (model.isBlockBusterArmed()) {
            st.append("  •  Blast armed");
        }
        powerStatusLabel.setText(st.toString());
        boolean canSpend = ch > 0 && !model.isPaused() && !model.isOver();
        powerFreezeBtn.setEnabled(canSpend);
        powerEasyBtn.setEnabled(canSpend);
        powerBlastBtn.setEnabled(canSpend);
        hudLines.setText(String.valueOf(model.getLinesCleared()));
        hudStreak.setText(String.valueOf(model.getLineStreak()));
        hudMult.setText("x" + model.getMult());
        hudAcc.setText(model.accuracyPercent() + "%");
        hudLevel.setText(String.valueOf(model.runLevelFromLines()));
        int lv = model.getLives();
        int lmax = model.getStartingLives();
        hudLives.setText("\u2665".repeat(Math.max(0, lv)) + "\u2661".repeat(Math.max(0, lmax - lv)));
        if (lv <= 1) {
            hudLives.setForeground(TypeTrisTheme.ERROR);
        } else if (lv <= 2) {
            hudLives.setForeground(TypeTrisTheme.YELLOW_MEDIUM);
        } else {
            hudLives.setForeground(TypeTrisTheme.PRIMARY);
        }
        // Web: input.disabled while paused (gameplay.js setPausedUI).
        typing.setEnabled(!model.isPaused() && !model.isOver());
        GameplayModel.Piece pc = model.getPiece();
        if (pc != null && !model.isOver()) {
            if (!pc.unlocked) {
                hintTyping.setText("Target: " + pc.word);
                // Web spawnPiece: input.value = "". Keep field aligned with model when a new word spawns.
                if (!Objects.equals(pc.word, lastTypingTargetWord)) {
                    lastTypingTargetWord = pc.word;
                    ignoreTypingDoc = true;
                    String shown = model.currentTypingDisplay();
                    typing.setText(shown);
                    typing.setCaretPosition(shown.length());
                    ignoreTypingDoc = false;
                    SwingUtilities.invokeLater(() -> {
                        typing.requestFocusInWindow();
                        moveCaretToTypingEnd();
                    });
                }
            } else {
                hintTyping.setText("Arrows + Space — lock before the stack hits the top");
                lastTypingTargetWord = null;
            }
        } else if (model.isOver()) {
            hintTyping.setText("");
            lastTypingTargetWord = null;
            ignoreTypingDoc = true;
            typing.setText("");
            ignoreTypingDoc = false;
        }
    }
 
    /**
     * Esc → pause only. Do not bind arrows/space here: {@code WHEN_IN_FOCUSED_WINDOW} runs before the
     * typing field's listeners and would consume arrow keys while the word is still locked, so the caret
     * could not move (web only handles arrows after unlock — {@code gameplay.js}).
     */
    private void installKeyboardActions() {
        int cond = JComponent.WHEN_IN_FOCUSED_WINDOW;
        getInputMap(cond).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        getActionMap().put("esc", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (model == null) {
                    return;
                }
                model.togglePause();
                pauseOverlay.setVisible(model.isPaused());
                audio.setBgmPaused(model.isPaused());
                if (!model.isPaused()) {
                    refocusTypingIfNeeded();
                }
                syncHud();
            }
        });
        getInputMap(cond).put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), "pow1");
        getInputMap(cond).put(KeyStroke.getKeyStroke(KeyEvent.VK_2, 0), "pow2");
        getInputMap(cond).put(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0), "pow3");
        getActionMap().put("pow1", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tryActivatePowerup(1);
            }
        });
        getActionMap().put("pow2", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tryActivatePowerup(2);
            }
        });
        getActionMap().put("pow3", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tryActivatePowerup(3);
            }
        });
    }

    /**
     * After unlock, focus may be on this panel (see document listener). Arrows/space must still move the
     * piece; the typing field listener only runs when the field has focus.
     */
    private void installPlacementKeyListenerOnPanel() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (model == null || model.isOver() || model.isPaused()) {
                    return;
                }
                GameplayModel.Piece pc = model.getPiece();
                if (pc == null || !pc.unlocked) {
                    return;
                }
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_SPACE) {
                    e.consume();
                    model.onSpace();
                    board.repaint();
                    return;
                }
                switch (k) {
                    case KeyEvent.VK_LEFT -> {
                        e.consume();
                        model.onArrowLeft();
                    }
                    case KeyEvent.VK_RIGHT -> {
                        e.consume();
                        model.onArrowRight();
                    }
                    case KeyEvent.VK_UP -> {
                        e.consume();
                        model.onArrowUp();
                    }
                    case KeyEvent.VK_DOWN -> {
                        e.consume();
                        model.onArrowDown();
                    }
                    default -> {
                        return;
                    }
                }
                board.repaint();
            }
        });
    }
 
    /**
     * After unpause, restore focus so keys work: typing field while the word is locked, otherwise this
     * panel (placement keys do not run when focus stays on Resume / Pause).
     */
    private void refocusTypingIfNeeded() {
        if (model == null || model.isOver() || model.isPaused()) {
            return;
        }
        GameplayModel.Piece p = model.getPiece();
        if (p == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (p.unlocked) {
                GameplayPanel.this.requestFocusInWindow();
            } else {
                typing.requestFocusInWindow();
                moveCaretToTypingEnd();
            }
        });
    }
 
    /** Call when this panel is shown so typing works immediately. */
    public void requestTypingFocus() {
        typing.requestFocusInWindow();
        moveCaretToTypingEnd();
    }
 
    /** Keep caret at end of the typing field so the next character appends after programmatic {@code setText}. */
    private void moveCaretToTypingEnd() {
        String t = typing.getText();
        typing.setCaretPosition(Math.max(0, t.length()));
    }
 
    /** Stop the tick timer when leaving the gameplay card to avoid background CPU use. */
    public void stopTimer() {
        if (gameTimer != null) {
            gameTimer.stop();
        }
        audio.shutdown();
    }
 
    public GameplayModel getModel() {
        return model;
    }
 
    /** Placeholder for a full in-panel restart; the shell normally rebuilds {@link GameplayPanel} instead. */
    public void restartSameLevel() {
        stopTimer();
        removeAll();
    }
 
    /** Renders the locked grid and the active piece with per-cell styling (typed / wrong / unlocked). */
    private final class BoardCanvas extends JPanel {
        private final int cell = BOARD_CELL_PX;
 
        BoardCanvas() {
            setOpaque(true);
            setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        }
 
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
 
            int w = GameplayModel.COLS * cell;
            int h = GameplayModel.ROWS * cell;

            var grid = model.getGrid();
            for (int r = 0; r < GameplayModel.ROWS; r++) {
                for (int c = 0; c < GameplayModel.COLS; c++) {
                    if (grid[r][c] != null) {
                        drawCell(g2, c, r, String.valueOf(grid[r][c].ch()), TypeTrisTheme.PRIMARY_CONTAINER, TypeTrisTheme.ON_PRIMARY);
                    }
                }
            }
 
            GameplayModel.Piece pc = model.getPiece();
            if (pc != null && !model.isOver()) {
                int minX = 999;
                int maxX = -999;
                int maxY = -999;
                for (GameplayModel.Part pt : pc.parts) {
                    minX = Math.min(minX, pt.x);
                    maxX = Math.max(maxX, pt.x);
                    maxY = Math.max(maxY, pt.y);
                }
                for (GameplayModel.Part pt : pc.parts) {
                    boolean wrong = pc.lastWrongIndex != null && pc.lastWrongIndex == pt.wi;
                    boolean typed = pt.wi < pc.lastTypedLen;
                    Color bg = TypeTrisTheme.PRIMARY_CONTAINER;
                    Color fg = TypeTrisTheme.ON_PRIMARY;
                    if (wrong) {
                        bg = new Color(0x40, 0x10, 0x18);
                        fg = Color.WHITE;
                    } else if (typed) {
                        bg = TypeTrisTheme.PRIMARY_CONTAINER.darker();
                        fg = TypeTrisTheme.SECONDARY;
                    }
                    drawCell(g2, pc.col + pt.x, pc.row + pt.y, String.valueOf(pt.ch), bg, fg);
                }
                if (pc.word != null) {
                    g2.setFont(TypeTrisTheme.label(10f, Font.BOLD));
                    g2.setColor(TypeTrisTheme.PRIMARY);
                    int lx = (pc.col + minX) * cell;
                    int ly = (pc.row + maxY + 1) * cell;
                    g2.drawString(pc.word, lx, ly + 14);
                }
            }

            g2.setStroke(new BasicStroke(1f));
            Color faintGrid = new Color(
                    TypeTrisTheme.OUTLINE_VARIANT.getRed(),
                    TypeTrisTheme.OUTLINE_VARIANT.getGreen(),
                    TypeTrisTheme.OUTLINE_VARIANT.getBlue(),
                    64);
            g2.setColor(faintGrid);
            for (int c = 0; c <= GameplayModel.COLS; c++) {
                int x = c * cell;
                g2.drawLine(x, 0, x, h);
            }
            for (int r = 0; r <= GameplayModel.ROWS; r++) {
                int y = r * cell;
                g2.drawLine(0, y, w, y);
            }

            g2.setStroke(new BasicStroke(2f));
            g2.setColor(TypeTrisTheme.PRIMARY);
            g2.drawRect(0, 0, w - 1, h - 1);
        }
 
        private void drawCell(Graphics2D g2, int col, int row, String ch, Color bg, Color fg) {
            int x = col * cell;
            int y = row * cell;
            int s = cell;
            g2.setColor(bg);
            g2.fillRect(x, y, s, s);
            g2.setColor(fg);
            g2.setFont(TypeTrisTheme.headline(14f, Font.BOLD));
            var fm = g2.getFontMetrics();
            int tx = x + (s - fm.stringWidth(ch)) / 2;
            int ty = y + (s + fm.getAscent()) / 2 - 2;
            g2.drawString(ch, tx, ty);
        }
    }
}
