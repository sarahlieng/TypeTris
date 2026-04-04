package typetris.gameplay;
 
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
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
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
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
import typetris.ui.ButtonRolloverSupport;
import typetris.ui.ClientPrefs;
import typetris.ui.Screens;
import typetris.ui.TypeTrisTheme;
 
/**
 * Swing gameplay screen: left HUD, grid canvas, typing row, top action bar, and pause overlay.
 * <p>
 * Navigation uses {@link typetris.ui.Screens} card names via {@code navigateReplaceCard} (e.g. game over,
 * quit to player menu, options). Persists bests / unlocks through {@link typetris.ui.ClientPrefs} and
 * posts scores through {@link typetris.backend.AuthRepository#recordGameResult}.
 */
public final class GameplayPanel extends JPanel {
 
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
    private final JLabel hudWpm = new JLabel("0");
    // CHANGE 1: renamed hudWpmNow → hudWpmOverall; label text updated to "Overall: 0 WPM"
    // to match #hud-wpm-overall in gameplay.html (small sub-label beneath the large WPM counter).
    private final JLabel hudWpmOverall = new JLabel("Overall: 0 WPM");
    private final JLabel hudLines = new JLabel("0");
    private final JLabel hudStreak = new JLabel("0");
    private final JLabel hudMult = new JLabel("x1");
    private final JLabel hudAcc = new JLabel("100%");
    private final JLabel hudLevel = new JLabel("1");
    private final JProgressBar levelBar = new JProgressBar(0, 100);
    private final JLabel phaseLabel = new JLabel("Type the word");
    // CHANGE 2: new field — mirrors <p id="falling-word-hint"> below the playfield in gameplay.html.
    private final JLabel fallingWordHint = new JLabel(" ");
    /** Semi-transparent layer over the play area while {@link GameplayModel#isPaused()}. */
    private final JPanel pauseOverlay = new JPanel();
    /** Prevents {@link DocumentListener} recursion when the model rewrites the typing field. */
    private boolean ignoreTypingDoc;
 
    /** ~60 FPS game step; drives {@link GameplayModel#tick(double)} and board repaint. */
    private Timer gameTimer;
    /** Snapshot for the game-over screen after {@link GameplayModel.Listener#onGameOver()}. */
    private int lastScore;
 
    /** Last piece word we showed a typing prompt for (refocus typing when a new word spawns). */
    private String lastTypingTargetWord;
 
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
            public void onGameOver() {
                if (model == null) return;
                lastScore = model.getScore();
                ClientPrefs.putBestScoreIfHigher(username, tier, subLevel, model.getScore());
                // Clearing a sub-level unlocks the next within the same tier (matches web localStorage).
                ClientPrefs.recordSubLevelClear(username, tier, subLevel);
                try {
                    auth.recordGameResult(
                            username,
                            model.getScore(),
                            model.wpmOverall(),
                            model.accuracyPercent());
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
        board.setPreferredSize(new Dimension(GameplayModel.COLS * 28 + 4, GameplayModel.ROWS * 28 + 4));
        board.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                GameplayPanel.this.requestFocusInWindow();
            }
        });
        playCol.add(board, BorderLayout.CENTER);
 
        // CHANGE 4: wrap fallingWordHint + typingRow together in belowBoard so both can sit
        // south of the board. Previously typingRow went straight into playCol SOUTH and
        // fallingWordHint did not exist. Mirrors <p id="falling-word-hint"> + typing area in HTML.
        JPanel belowBoard = new JPanel(new BorderLayout(0, 4));
        belowBoard.setOpaque(false);
        fallingWordHint.setFont(TypeTrisTheme.label(10f, Font.BOLD));
        fallingWordHint.setForeground(TypeTrisTheme.PRIMARY);
        fallingWordHint.setHorizontalAlignment(JLabel.CENTER);
        belowBoard.add(fallingWordHint, BorderLayout.NORTH);
 
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
                    typing.setText("");
                    typing.setCaretPosition(0);
                    ignoreTypingDoc = false;
                    return;
                }
                model.onTypingFieldChanged(typing.getText());
                ignoreTypingDoc = true;
                String shown = model.currentTypingDisplay();
                typing.setText(shown);
                typing.setCaretPosition(shown.length());
                ignoreTypingDoc = false;
                GameplayModel.Piece pcAfter = model.getPiece();
                if (pcAfter != null && pcAfter.unlocked) {
                    SwingUtilities.invokeLater(GameplayPanel.this::requestFocusInWindow);
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(this::syncTypingFromDocumentAfterNotification);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(this::syncTypingFromDocumentAfterNotification);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(this::syncTypingFromDocumentAfterNotification);
            }
        });
 
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
 
        // show top bar with pause / options / quit buttons
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topBar.setOpaque(false);
        JButton pauseBtn = neonButton("Pause", TypeTrisTheme.PRIMARY_CONTAINER, TypeTrisTheme.ON_PRIMARY);
        pauseBtn.addActionListener(e -> {
            model.togglePause();
            pauseOverlay.setVisible(model.isPaused());
            if (!model.isPaused()) {
                refocusTypingIfNeeded();
            }
        });
        JButton optBtn = neonButton("Options", TypeTrisTheme.SURFACE_CONTAINER, TypeTrisTheme.ON_SURFACE);
        optBtn.addActionListener(e -> navigateReplaceCard.accept(Screens.DIFFICULTY));
        JButton quitBtn = neonButton("Quit", TypeTrisTheme.SURFACE_CONTAINER, TypeTrisTheme.ON_SURFACE_VARIANT);
        quitBtn.addActionListener(e -> quitWithUnsavedWarning());
        topBar.add(pauseBtn);
        topBar.add(optBtn);
        topBar.add(quitBtn);
 
        // Stack main layout + pause overlay in one fixed-size band (matches compact desktop layout).
        JLayeredPane layered = new JLayeredPane();
        layered.setPreferredSize(new Dimension(900, 560));
        wrapCenter(layered, topBar, center, pauseOverlay);
 
        model.startRound(); // start round after UI is built
 
        installKeyboardActions();
        installPlacementKeyListenerOnPanel();
        gameTimer = new Timer(16, e -> {
            model.tick(0.016);
            board.repaint();
        });
        gameTimer.start();
 
        // HUD already refreshed by startRound() so fireHud()
 
        // overlay is added in wrapCenter before children here
        pauseOverlay.setLayout(new GridLayout(0, 1, 0, 12));
        pauseOverlay.setBackground(new Color(0, 0, 0, 200));
        pauseOverlay.setOpaque(true);
        pauseOverlay.setVisible(false);
        JButton resume = neonButton("Resume", TypeTrisTheme.PRIMARY_CONTAINER, TypeTrisTheme.ON_PRIMARY);
        resume.addActionListener(e -> {
            model.setPaused(false);
            pauseOverlay.setVisible(false);
            refocusTypingIfNeeded();
        });
        pauseOverlay.add(resume);
        // CHANGE 5: pause menu matches web — Resume, Options, Quit Game (no save stub).
        JButton pauseOptions = neonButton("Options", TypeTrisTheme.SURFACE_CONTAINER, TypeTrisTheme.ON_SURFACE);
        pauseOptions.addActionListener(e -> navigateReplaceCard.accept(Screens.DIFFICULTY));
        pauseOverlay.add(pauseOptions);
        JButton pauseQuit = neonButton("Quit Game", TypeTrisTheme.SURFACE_CONTAINER, TypeTrisTheme.ON_SURFACE_VARIANT);
        pauseQuit.addActionListener(e -> quitWithUnsavedWarning());
        pauseOverlay.add(pauseQuit);
    }

    private void quitWithUnsavedWarning() {
        int r = JOptionPane.showConfirmDialog(
                this,
                "Your progress will not be saved.",
                "Quit game",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.OK_OPTION) {
            navigateReplaceCard.accept(Screens.PLAYER);
        }
    }
 
    /** Places the main stack on the default layer and the pause strip on top. */
    private void wrapCenter(JLayeredPane layered, JPanel topBar, JPanel center, JPanel overlay) {
        JPanel stack = new JPanel(new BorderLayout());
        stack.setOpaque(false);
        stack.add(topBar, BorderLayout.NORTH);
        stack.add(center, BorderLayout.CENTER);
        stack.setBounds(0, 0, 900, 560);
        layered.add(stack, JLayeredPane.DEFAULT_LAYER);
        // CHANGE 6: overlay now covers the full panel (0,0,900,560) instead of a 140px strip at y=420.
        // Matches #pause-overlay { position: fixed; inset: 0 } in gameplay.html.
        overlay.setBounds(0, 0, 900, 560);
        layered.add(overlay, JLayeredPane.PALETTE_LAYER);
        add(layered, BorderLayout.CENTER);
    }
 
    /** Final score from the last completed run; read by the shell when showing game over. */
    public int getLastScore() {
        return lastScore;
    }
 
    /** Small bordered control matching the neon / high-contrast web buttons. */
    private JButton neonButton(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFont(TypeTrisTheme.label(11f, Font.BOLD));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1),
                        new EmptyBorder(6, 14, 6, 14)));
        ButtonRolloverSupport.install(b, bg);
        return b;
    }
 
    private JPanel buildHudColumn() {
        JPanel p = new JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        p.setBorder(new LineBorder(TypeTrisTheme.PRIMARY.darker(), 1));
 
        p.add(hudBlock("SCORE", hudScore, 28f));
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(hudBlock("BEST SCORE", hudBest, 22f));
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(hudBlock("WORDS PER MINUTE", hudWpm, 24f));
        // CHANGE 7a: pass hudWpmOverall (renamed from hudWpmNow) to hudTiny.
        p.add(hudTiny(hudWpmOverall));
        p.add(javax.swing.Box.createVerticalStrut(8));
        // CHANGE 7b: LINES CLEARED, STREAK, COMBO, ACCURACY, LEVEL removed from here —
        // they now live in buildRightColumn() matching the right col-span-3 panel in gameplay.html.
        p.add(javax.swing.Box.createVerticalStrut(12));
        JLabel cTitle = new JLabel("CONTROLS");
        cTitle.setFont(TypeTrisTheme.label(10f, Font.BOLD));
        cTitle.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        p.add(cTitle);
        phaseLabel.setFont(TypeTrisTheme.headline(14f, Font.BOLD));
        phaseLabel.setForeground(TypeTrisTheme.PRIMARY_CONTAINER);
        p.add(phaseLabel);
 
        // CHANGE 8: HOW TO PLAY section added at the bottom of the left panel to match
        // the second card in the col-span-3 left section of gameplay.html.
        p.add(javax.swing.Box.createVerticalStrut(16));
        JLabel howTitle = new JLabel("HOW TO PLAY");
        howTitle.setFont(TypeTrisTheme.label(10f, Font.BOLD));
        howTitle.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        p.add(howTitle);
        p.add(javax.swing.Box.createVerticalStrut(4));
        String[] tips = {
            "<html>• Type the word to unlock move / rotate; if it lands first, it locks there.</html>",
            "<html>• ← → move, ↑ ↓ rotate, Space to drop &amp; lock.</html>",
            "<html>• Fill rows to clear them and score.</html>",
            "<html>• Game ends if the stack reaches the top.</html>"
        };
        for (String tip : tips) {
            JLabel l = new JLabel(tip);
            l.setFont(TypeTrisTheme.label(11f, Font.PLAIN));
            l.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
            p.add(l);
        }
 
        return p;
    }
 
    /**
     * Right column panel matching the col-span-3 right section in gameplay.html.
     * Contains LEVEL &amp; PROGRESS (#hud-current-level, #hud-lines-cleared, #hud-level-progress-fill)
     * and COMBO &amp; STREAK (#hud-streak, #hud-combo-multiplier, #hud-accuracy).
     * Previously these metrics were all in buildHudColumn() on the left.
     */
    private JPanel buildRightColumn() {
        JPanel p = new JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        p.setBorder(new LineBorder(TypeTrisTheme.PRIMARY.darker(), 1));
 
        // LEVEL & PROGRESS section (#hud-current-level, #hud-lines-cleared, #hud-level-progress-fill)
        p.add(hudBlock("CURRENT LEVEL:", hudLevel, 28f));
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(hudBlock("LINES CLEARED:", hudLines, 22f));
        p.add(javax.swing.Box.createVerticalStrut(8));
        levelBar.setForeground(TypeTrisTheme.PRIMARY_CONTAINER);
        levelBar.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        levelBar.setBorder(new LineBorder(TypeTrisTheme.OUTLINE_VARIANT));
        p.add(levelBar);
        JLabel nextLvl = new JLabel("NEXT LEVEL");
        nextLvl.setFont(TypeTrisTheme.label(9f, Font.BOLD));
        nextLvl.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        p.add(nextLvl);
 
        p.add(javax.swing.Box.createVerticalStrut(12));
 
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
        return b;
    }
 
    private JLabel hudTiny(JLabel l) {
        l.setFont(TypeTrisTheme.label(11f, Font.PLAIN));
        l.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        return l;
    }
 
    /** Copies all HUD labels and the level bar from the model. */
    private void syncHud() {
        if (model == null) {
            return;
        }
        hudScore.setText(String.valueOf(model.getScore()));
        hudBest.setText(String.valueOf(model.getBestDisplayed()));
        // CHANGE 9: WPM labels were swapped — fixed to match gameplay.html element IDs.
        // #hud-wpm is the large live counter → wpmInstant().
        // #hud-wpm-overall is the small sub-label → wpmOverall().
        hudWpm.setText(String.valueOf(model.wpmInstant()));
        hudWpmOverall.setText("Overall: " + model.wpmOverall() + " WPM");
        hudLines.setText(String.valueOf(model.getLinesCleared()));
        hudStreak.setText(String.valueOf(model.getLineStreak()));
        hudMult.setText("x" + model.getMult());
        hudAcc.setText(model.accuracyPercent() + "%");
        hudLevel.setText(String.valueOf(model.runLevelFromLines()));
        levelBar.setValue(model.levelProgressPercent());
        phaseLabel.setText(model.controlPhaseLabel());
        // Web: input.disabled while paused (gameplay.js setPausedUI).
        typing.setEnabled(!model.isPaused() && !model.isOver());
        GameplayModel.Piece pc = model.getPiece();
        if (pc != null && !model.isOver()) {
            // CHANGE 10: populate fallingWordHint — mirrors #falling-word-hint in gameplay.html.
            fallingWordHint.setText(model.fallingWordHint());
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
            fallingWordHint.setText("");
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
                if (!model.isPaused()) {
                    refocusTypingIfNeeded();
                }
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
        private final int cell = 28;
 
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
            g2.setColor(TypeTrisTheme.OUTLINE_VARIANT.darker());
            for (int c = 0; c <= GameplayModel.COLS; c++) {
                g2.drawLine(c * cell, 0, c * cell, h);
            }
            for (int r = 0; r <= GameplayModel.ROWS; r++) {
                g2.drawLine(0, r * cell, w, r * cell);
            }
 
            var grid = model.getGrid();
            for (int r = 0; r < GameplayModel.ROWS; r++) {
                for (int c = 0; c < GameplayModel.COLS; c++) {
                    if (grid[r][c] != null) {
                        drawCell(g2, c, r, String.valueOf(grid[r][c].ch()), TypeTrisTheme.PRIMARY_CONTAINER, TypeTrisTheme.ON_PRIMARY, false, false);
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
                    boolean unlocked = pc.unlocked;
                    if (wrong) {
                        bg = new Color(0x40, 0x10, 0x18);
                        fg = Color.WHITE;
                    } else if (typed) {
                        bg = TypeTrisTheme.PRIMARY_CONTAINER.darker();
                        fg = TypeTrisTheme.SECONDARY;
                    }
                    drawCell(g2, pc.col + pt.x, pc.row + pt.y, String.valueOf(pt.ch), bg, fg, unlocked, wrong);
                }
                if (pc.word != null) {
                    g2.setFont(TypeTrisTheme.label(10f, Font.BOLD));
                    g2.setColor(TypeTrisTheme.PRIMARY);
                    int lx = (pc.col + minX) * cell;
                    int ly = (pc.row + maxY + 1) * cell;
                    g2.drawString(pc.word, lx, ly + 14);
                }
            }
        }
 
        private void drawCell(Graphics2D g2, int col, int row, String ch, Color bg, Color fg, boolean unlocked, boolean wrong) {
            int x = col * cell + 1;
            int y = row * cell + 1;
            int s = cell - 2;
            g2.setColor(bg);
            g2.fillRoundRect(x, y, s, s, 4, 4);
            if (unlocked) {
                g2.setColor(TypeTrisTheme.SECONDARY);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(x, y, s, s, 4, 4);
            } else if (wrong) {
                g2.setColor(TypeTrisTheme.ERROR);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(x, y, s, s, 4, 4);
            } else {
                g2.setColor(TypeTrisTheme.PRIMARY.darker());
                g2.drawRoundRect(x, y, s, s, 4, 4);
            }
            g2.setColor(fg);
            g2.setFont(TypeTrisTheme.headline(14f, Font.BOLD));
            var fm = g2.getFontMetrics();
            int tx = x + (s - fm.stringWidth(ch)) / 2;
            int ty = y + (s + fm.getAscent()) / 2 - 2;
            g2.drawString(ch, tx, ty);
        }
    }
}
