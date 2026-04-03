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
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
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
    /** Unused hook reserved for parity with a “return to menu” callback; quit uses {@code navigateReplaceCard}. */
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
    private final JLabel hudWpmNow = new JLabel("Now: 0 WPM");
    private final JLabel hudLines = new JLabel("0");
    private final JLabel hudStreak = new JLabel("0");
    private final JLabel hudMult = new JLabel("x1");
    private final JLabel hudAcc = new JLabel("100%");
    private final JLabel hudLevel = new JLabel("1");
    private final JProgressBar levelBar = new JProgressBar(0, 100);
    private final JLabel phaseLabel = new JLabel("Type the word");
    /** Semi-transparent layer over the play area while {@link GameplayModel#isPaused()}. */
    private final JPanel pauseOverlay = new JPanel();
    /** Prevents {@link DocumentListener} recursion when the model rewrites the typing field. */
    private boolean ignoreTypingDoc;

    /** ~60 FPS game step; drives {@link GameplayModel#tick(double)} and board repaint. */
    private Timer gameTimer;
    /** Snapshot for the game-over screen after {@link GameplayModel.Listener#onGameOver()}. */
    private int lastScore;

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

        // Model: tier/subLevel select fall speed and word length; best score seeds HUD “best”.
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

        // —— Center: HUD | board + typing ——
        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel hudCol = buildHudColumn();
        center.add(hudCol, BorderLayout.WEST);

        JPanel playCol = new JPanel(new BorderLayout());
        playCol.setOpaque(false);
        board.setPreferredSize(new Dimension(GameplayModel.COLS * 28 + 4, GameplayModel.ROWS * 28 + 4));
        playCol.add(board, BorderLayout.CENTER);

        JPanel typingRow = new JPanel(new BorderLayout(8, 4));
        typingRow.setOpaque(false);
        typing.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        typing.setForeground(TypeTrisTheme.ON_SURFACE);
        typing.setCaretColor(TypeTrisTheme.PRIMARY);
        typing.setFont(TypeTrisTheme.body(18f, Font.PLAIN));
        typing.setBorder(new LineBorder(TypeTrisTheme.OUTLINE, 1));
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
        // Pipe keystrokes into the model; model returns display string (prefix / masking) for the field.
        typing.getDocument().addDocumentListener(new DocumentListener() {
            private void upd() {
                if (ignoreTypingDoc || model.isOver() || model.isPaused()) {
                    return;
                }
                model.onTypingFieldChanged(typing.getText());
                ignoreTypingDoc = true;
                typing.setText(model.currentTypingDisplay());
                ignoreTypingDoc = false;
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                upd();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                upd();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                upd();
            }
        });

        hintTyping.setFont(TypeTrisTheme.label(11f, Font.BOLD));
        hintTyping.setForeground(TypeTrisTheme.PRIMARY);
        typingRow.add(typing, BorderLayout.CENTER);
        typingRow.add(hintTyping, BorderLayout.SOUTH);
        playCol.add(typingRow, BorderLayout.SOUTH);

        center.add(playCol, BorderLayout.CENTER);

        // —— Top bar: pause / options / stub save / quit ——
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topBar.setOpaque(false);
        JButton pauseBtn = neonButton("Pause", TypeTrisTheme.PRIMARY_CONTAINER, TypeTrisTheme.ON_PRIMARY);
        pauseBtn.addActionListener(e -> {
            model.togglePause();
            pauseOverlay.setVisible(model.isPaused());
        });
        JButton optBtn = neonButton("Options", TypeTrisTheme.SURFACE_CONTAINER, TypeTrisTheme.ON_SURFACE);
        optBtn.addActionListener(e -> navigateReplaceCard.accept(Screens.DIFFICULTY));
        JButton svBtn = neonButton("Save", TypeTrisTheme.SURFACE_CONTAINER, TypeTrisTheme.ON_SURFACE);
        svBtn.addActionListener(e -> javax.swing.JOptionPane.showMessageDialog(this, "Save game is not implemented yet."));
        JButton quitBtn = neonButton("Quit", TypeTrisTheme.SURFACE_CONTAINER, TypeTrisTheme.ON_SURFACE_VARIANT);
        quitBtn.addActionListener(e -> navigateReplaceCard.accept(Screens.PLAYER));
        topBar.add(pauseBtn);
        topBar.add(optBtn);
        topBar.add(svBtn);
        topBar.add(quitBtn);

        // Stack main layout + pause overlay in one fixed-size band (matches compact desktop layout).
        JLayeredPane layered = new JLayeredPane();
        layered.setPreferredSize(new Dimension(900, 560));
        wrapCenter(layered, topBar, center, pauseOverlay);

        installKeyboardActions();
        gameTimer = new Timer(16, e -> {
            model.tick(0.016);
            board.repaint();
        });
        gameTimer.start();

        syncHud();

        // Note: overlay is added in wrapCenter before children here; mutating the same JPanel is OK on EDT.
        pauseOverlay.setLayout(new GridLayout(0, 1, 0, 12));
        pauseOverlay.setBackground(new Color(0, 0, 0, 200));
        pauseOverlay.setOpaque(true);
        pauseOverlay.setVisible(false);
        JButton resume = neonButton("Resume", TypeTrisTheme.PRIMARY_CONTAINER, TypeTrisTheme.ON_PRIMARY);
        resume.addActionListener(e -> {
            model.setPaused(false);
            pauseOverlay.setVisible(false);
        });
        pauseOverlay.add(resume);
    }

    /** Places the main stack on the default layer and the pause strip on top. */
    private void wrapCenter(JLayeredPane layered, JPanel topBar, JPanel center, JPanel overlay) {
        JPanel stack = new JPanel(new BorderLayout());
        stack.setOpaque(false);
        stack.add(topBar, BorderLayout.NORTH);
        stack.add(center, BorderLayout.CENTER);
        stack.setBounds(0, 0, 900, 560);
        layered.add(stack, JLayeredPane.DEFAULT_LAYER);
        overlay.setBounds(0, 420, 900, 140);
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
        b.setBorder(new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1));
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
        p.add(hudTiny(hudWpmNow));
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(hudBlock("LINES CLEARED", hudLines, 18f));
        p.add(hudBlock("STREAK", hudStreak, 18f));
        p.add(hudBlock("COMBO", hudMult, 18f));
        p.add(hudBlock("ACCURACY", hudAcc, 18f));
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(hudBlock("LEVEL", hudLevel, 18f));
        levelBar.setForeground(TypeTrisTheme.PRIMARY_CONTAINER);
        levelBar.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        levelBar.setBorder(new LineBorder(TypeTrisTheme.OUTLINE_VARIANT));
        p.add(levelBar);
        p.add(javax.swing.Box.createVerticalStrut(12));
        JLabel cTitle = new JLabel("CONTROLS");
        cTitle.setFont(TypeTrisTheme.label(10f, Font.BOLD));
        cTitle.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        p.add(cTitle);
        phaseLabel.setFont(TypeTrisTheme.headline(14f, Font.BOLD));
        phaseLabel.setForeground(TypeTrisTheme.PRIMARY_CONTAINER);
        p.add(phaseLabel);
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
        hudScore.setText(String.valueOf(model.getScore()));
        hudBest.setText(String.valueOf(model.getBestDisplayed()));
        hudWpm.setText(String.valueOf(model.wpmOverall()));
        hudWpmNow.setText("Now: " + model.wpmInstant() + " WPM");
        hudLines.setText(String.valueOf(model.getLinesCleared()));
        hudStreak.setText(String.valueOf(model.getLineStreak()));
        hudMult.setText("x" + model.getMult());
        hudAcc.setText(model.accuracyPercent() + "%");
        hudLevel.setText(String.valueOf(model.runLevelFromLines()));
        levelBar.setValue(model.levelProgressPercent());
        phaseLabel.setText(model.controlPhaseLabel());
        GameplayModel.Piece pc = model.getPiece();
        if (pc != null && !model.isOver()) {
            hintTyping.setText("Target: " + pc.word);
        } else if (model.isOver()) {
            hintTyping.setText("");
        }
    }

    /** Arrow keys, space (soft drop), Esc (pause) — active even when the typing field has focus. */
    private void installKeyboardActions() {
        int cond = JComponent.WHEN_IN_FOCUSED_WINDOW;
        getInputMap(cond).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
        getInputMap(cond).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
        getInputMap(cond).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
        getInputMap(cond).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
        getInputMap(cond).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "space");
        getInputMap(cond).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        getActionMap().put("left", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                model.onArrowLeft();
            }
        });
        getActionMap().put("right", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                model.onArrowRight();
            }
        });
        getActionMap().put("up", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                model.onArrowUp();
            }
        });
        getActionMap().put("down", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                model.onArrowDown();
            }
        });
        getActionMap().put("space", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                model.onSpace();
            }
        });
        getActionMap().put("esc", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                model.togglePause();
                pauseOverlay.setVisible(model.isPaused());
            }
        });
    }

    /** Call when this panel is shown so typing works immediately. */
    public void requestTypingFocus() {
        typing.requestFocusInWindow();
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
