package typetris.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Eight-step HOW TO PLAY flow matching the web
 * {@code instructions/instructions*.html} tutorials
 * (labels, titles, and body copy), adapted for the desktop client (controls,
 * scoring)
 *
 * @author CS2212 Group 26, Western University
 */
final class InstructionsScreens {

    private static final int PAGE_COUNT = 8;
    /** Progress segments in the bottom nav: fixed size so every step matches */
    private static final int DOT_SEGMENT_W = 24;
    private static final int DOT_SEGMENT_H = 4;
    /** Vertical space after page title (all tutorials) */
    private static final int GAP_AFTER_TITLE = 10;
    /** Space between stacked sections */
    private static final int GAP_SECTION = 12;
    /** Max width for tutorial text blocks so lines wrap readably (centered in scroll view) */
    private static final int INSTRUCTIONS_CONTENT_W = 480;

    /** Prevents instantiation; use {@link #createWizard(Runnable)}. */
    private InstructionsScreens() {
    }

    /** Cap width so body text and cards don’t span the whole window */
    private static void capInstructionWidth(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension d = c.getPreferredSize();
        int h = (c instanceof JTextArea) ? Integer.MAX_VALUE : Math.max(d.height, 32);
        c.setMaximumSize(new Dimension(INSTRUCTIONS_CONTENT_W, h));
    }

    /**
     * Builds the full HOW TO PLAY wizard with header, paged content, and prev/next navigation.
     *
     * @param backToMenu runnable invoked when the user clicks Back (typically shows main menu)
     * @return root panel to embed on the instructions card
     */
    static JPanel createWizard(Runnable backToMenu) {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JButton menuBack = new JButton("Back");
        UiButtonStyles.applyBack(menuBack);
        menuBack.addActionListener(e -> backToMenu.run());
        header.add(menuBack, BorderLayout.WEST);

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        JLabel brand = new JLabel("TYPE-TRIS", SwingConstants.CENTER);
        brand.setFont(TypeTrisTheme.headline(22f, Font.BOLD));
        brand.setForeground(TypeTrisTheme.PRIMARY);
        brand.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        JLabel sub = new JLabel("HOW TO PLAY", SwingConstants.CENTER);
        sub.setFont(TypeTrisTheme.headline(11f, Font.BOLD));
        sub.setForeground(TypeTrisTheme.SECONDARY);
        sub.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        titles.add(brand);
        titles.add(Box.createVerticalStrut(4));
        titles.add(sub);
        header.add(titles, BorderLayout.CENTER);

        root.add(header, BorderLayout.NORTH);

        CardLayout cards = new CardLayout();
        JPanel deck = new JPanel(cards);
        deck.setOpaque(false);
        for (int i = 0; i < PAGE_COUNT; i++) {
            deck.add(wrapScroll(buildPage(i)), "p" + i);
        }

        root.add(deck, BorderLayout.CENTER);

        JPanel[] dots = new JPanel[PAGE_COUNT];
        for (int i = 0; i < PAGE_COUNT; i++) {
            JPanel d = new JPanel();
            Dimension dotSize = new Dimension(DOT_SEGMENT_W, DOT_SEGMENT_H);
            d.setPreferredSize(dotSize);
            d.setMinimumSize(dotSize);
            d.setMaximumSize(dotSize);
            d.setBackground(TypeTrisTheme.OUTLINE_VARIANT);
            dots[i] = d;
        }

        JButton prev = new JButton("Previous");
        JButton next = new JButton("Next");
        UiButtonStyles.applySecondary(prev);
        UiButtonStyles.applyPrimary(next);

        JPanel nav = new JPanel(new BorderLayout(12, 0));
        nav.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(prev);
        JPanel centerDots = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        centerDots.setOpaque(false);
        for (JPanel d : dots) {
            centerDots.add(d);
        }
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(next);
        nav.add(left, BorderLayout.WEST);
        nav.add(centerDots, BorderLayout.CENTER);
        nav.add(right, BorderLayout.EAST);
        root.add(nav, BorderLayout.SOUTH);

        final int[] index = { 0 };

        Runnable updateDots = () -> {
            for (int i = 0; i < PAGE_COUNT; i++) {
                dots[i].setBackground(i == index[0] ? TypeTrisTheme.SECONDARY : TypeTrisTheme.OUTLINE_VARIANT);
            }
            prev.setEnabled(index[0] > 0);
            next.setText(index[0] == PAGE_COUNT - 1 ? "Done" : "Next");
        };

        prev.addActionListener(e -> {
            if (index[0] > 0) {
                index[0]--;
                cards.show(deck, "p" + index[0]);
                updateDots.run();
            }
        });
        next.addActionListener(e -> {
            if (index[0] < PAGE_COUNT - 1) {
                index[0]++;
                cards.show(deck, "p" + index[0]);
                updateDots.run();
            } else {
                backToMenu.run();
            }
        });

        updateDots.run();
        return root;
    }

    /**
     * Wraps a tutorial page in a themed scroll pane with sensible scroll increments.
     *
     * @param page content panel for one wizard step
     * @return scroll pane ready to add to the card deck
     */
    private static JScrollPane wrapScroll(JPanel page) {
        page.setOpaque(false);
        JScrollPane sp = new JScrollPane(page);
        sp.setBorder(BorderFactory.createLineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1));
        sp.getViewport().setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        sp.setOpaque(true);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    /**
     * Builds one tutorial page (0-based index) with copy aligned to the web instructions.
     *
     * @param i page index from {@code 0} to {@code PAGE_COUNT - 1}
     * @return horizontally centered column panel inside a surface shell
     */
    private static JPanel buildPage(int i) {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(new EmptyBorder(12, 16, 16, 16));
        inner.setOpaque(false);
        inner.setMaximumSize(new Dimension(INSTRUCTIONS_CONTENT_W, Integer.MAX_VALUE));

        JLabel kicker = kickerLabel("TUTORIAL " + (i + 1));
        inner.add(kicker);
        inner.add(Box.createVerticalStrut(6));

        switch (i) {
            case 0 -> {
                inner.add(titleLabel("Welcome to TypeTris!"));
                inner.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                inner.add(callout(
                        "THE BASICS OF TYPING",
                        "When blocks fall, words appear on them. Type the words correctly before the block lands."));
                inner.add(Box.createVerticalStrut(GAP_SECTION));
                inner.add(
                        body(
                                "From the main menu you can register or log in, then use Start game to pick a tier and "
                                        + "sub-level. Parent/teacher tools use PIN 2212 (course demo)."));
            }
            case 1 -> {
                inner.add(titleLabel("Placing blocks"));
                inner.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                inner.add(body(
                        "Type the word on a falling block correctly to unlock its controls. Once you've typed the word "
                                + "accurately, you can rotate the block and move it left or right to position it on the "
                                + "board. Build complete rows and keep your stack low."));
                inner.add(Box.createVerticalStrut(GAP_SECTION));
                inner.add(twoCallouts("ACTION", "TYPE WORD", "ACTION", "POSITION"));
            }
            case 2 -> {
                inner.add(titleLabel("Clearing lines"));
                inner.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                inner.add(body(
                        "Fill complete horizontal rows to clear them and increase your score. "
                                + "More lines at once means a bigger bonus. Clearing four lines at once is the classic "
                                + "\"Tetris\" moment."));
            }
            case 3 -> {
                inner.add(titleLabel("Controls"));
                inner.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                inner.add(body(
                        "Type the word correctly to unlock block controls. Then use arrow keys: Left/Right to move, "
                                + "Up to rotate clockwise, Down to rotate counter-clockwise. Press Space to hard-drop "
                                + "and lock the piece where it lands."));
                inner.add(Box.createVerticalStrut(GAP_SECTION));
                inner.add(twoCallouts("Step 1", "TYPE", "Step 2", "MOVE"));
            }
            case 4 -> {
                inner.add(titleLabel("Game over"));
                inner.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                inner.add(body(
                        "If blocks stack to the top of the board, the game ends. Type quickly and accurately to survive "
                                + "the increasing fall speed as you clear lines."));
                inner.add(Box.createVerticalStrut(GAP_SECTION));
                inner.add(twoCallouts("THREAT LEVEL", "CRITICAL", "OBJECTIVE", "SURVIVE"));
            }
            case 5 -> {
                inner.add(titleLabel("Levels & difficulty"));
                inner.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                inner.add(calloutBox(
                        "Blocks fall faster and words get longer as you progress in a run. Each tier (Easy / Medium / "
                                + "Hard) and sub-level pushes your typing further."));
                inner.add(Box.createVerticalStrut(GAP_SECTION));
                inner.add(threeStats("BASE SPEED", "Tier set", "RUN LEVEL", "HUD ↑", "MULTIPLIER", "Streak"));
            }
            case 6 -> {
                inner.add(titleLabel("Power-up blocks"));
                inner.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                inner.add(body(
                        "In the game, type five words in a row with zero mistakes to earn a power-up charge. "
                                + "Spend a charge from the gameplay bar (or press 1 / 2 / 3) to activate an effect."));
                inner.add(Box.createVerticalStrut(GAP_SECTION));
                inner.add(
                        stripCard(
                                "T",
                                TypeTrisTheme.PRIMARY,
                                "TIME FREEZE",
                                "Slows or pauses the drop briefly so you can finish typing or position the piece."));
                inner.add(Box.createVerticalStrut(8));
                inner.add(
                        stripCard(
                                "E",
                                TypeTrisTheme.TERTIARY,
                                "EASY WORDS",
                                "Temporarily uses shorter or simpler words so you can recover under pressure."));
                inner.add(Box.createVerticalStrut(8));
                inner.add(
                        stripCard(
                                "B",
                                TypeTrisTheme.ERROR,
                                "BLOCK BUSTER",
                                "Clears extra nearby cells when you lock the piece, carving a path through the stack."));
            }
            case 7 -> {
                inner.add(titleLabel("Scoring"));
                inner.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                inner.add(calloutBox(
                        "Points for every correct word. Line-clear bonuses scale with how many rows you clear at once, "
                                + "multiplied by your current streak multiplier."));
                inner.add(Box.createVerticalStrut(GAP_SECTION));
                inner.add(
                        stripCard(
                                "S",
                                TypeTrisTheme.PRIMARY.darker(),
                                "SPEED BONUS",
                                "Faster play and higher tiers increase pressure; big clears reward you when you keep up."));
                inner.add(Box.createVerticalStrut(8));
                inner.add(
                        stripCard(
                                "A",
                                TypeTrisTheme.SECONDARY,
                                "ACCURACY & STREAK",
                                "Clean typing builds your line streak and raises the combo multiplier on the HUD."));
            }
            default -> inner.add(body(""));
        }
        inner.add(Box.createVerticalGlue());

        JPanel shell = new JPanel();
        shell.setLayout(new BoxLayout(shell, BoxLayout.X_AXIS));
        shell.setOpaque(true);
        shell.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        shell.add(Box.createHorizontalGlue());
        shell.add(inner);
        shell.add(Box.createHorizontalGlue());
        return shell;
    }

    /**
     * @param text small uppercase kicker (e.g. tutorial step)
     * @return left-aligned muted label
     */
    private static JLabel kickerLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(TypeTrisTheme.label(10f, Font.BOLD));
        l.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        capInstructionWidth(l);
        return l;
    }

    /**
     * @param text page title
     * @return primary-colored headline label
     */
    private static JLabel titleLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(TypeTrisTheme.headline(20f, Font.BOLD));
        l.setForeground(TypeTrisTheme.PRIMARY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        capInstructionWidth(l);
        return l;
    }

    /**
     * Non-editable wrapped paragraph for tutorial body copy.
     *
     * @param text paragraph text
     * @return configured {@link JTextArea}
     */
    private static JTextArea body(String text) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setWrapStyleWord(true);
        a.setLineWrap(true);
        a.setOpaque(false);
        a.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        a.setFont(TypeTrisTheme.body(14f, Font.PLAIN));
        a.setBorder(new EmptyBorder(0, 0, 0, 0));
        a.setAlignmentX(Component.LEFT_ALIGNMENT);
        a.setRows(0);
        a.setColumns(0);
        a.setSize(INSTRUCTIONS_CONTENT_W, Short.MAX_VALUE);
        Dimension pref = a.getPreferredSize();
        a.setPreferredSize(new Dimension(INSTRUCTIONS_CONTENT_W, pref.height));
        a.setMaximumSize(new Dimension(INSTRUCTIONS_CONTENT_W, Integer.MAX_VALUE));
        return a;
    }

    /**
     * Bordered callout with heading and paragraph.
     *
     * @param heading short title
     * @param paragraph body text
     * @return panel suitable for stacking in a page
     */
    private static JPanel callout(String heading, String paragraph) {
        JPanel wrap = new JPanel(new BorderLayout(0, 8));
        wrap.setOpaque(true);
        wrap.setBackground(TypeTrisTheme.SURFACE_CONTAINER);
        wrap.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.SECONDARY, 2),
                        new EmptyBorder(12, 14, 12, 14)));
        JLabel h = new JLabel(heading);
        h.setFont(TypeTrisTheme.headline(13f, Font.BOLD));
        h.setForeground(TypeTrisTheme.SECONDARY);
        wrap.add(h, BorderLayout.NORTH);
        JTextArea t = body(paragraph);
        wrap.add(t, BorderLayout.CENTER);
        capInstructionWidth(wrap);
        return wrap;
    }

    /**
     * Callout with body text only (no separate heading row).
     *
     * @param paragraph body text
     * @return bordered panel
     */
    private static JPanel calloutBox(String paragraph) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(true);
        wrap.setBackground(TypeTrisTheme.SURFACE_CONTAINER);
        wrap.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.SECONDARY, 2),
                        new EmptyBorder(14, 16, 14, 16)));
        wrap.add(body(paragraph), BorderLayout.CENTER);
        capInstructionWidth(wrap);
        return wrap;
    }

    /**
     * Two side-by-side stat-style tiles.
     *
     * @param a1 first label
     * @param v1 first value
     * @param a2 second label
     * @param v2 second value
     * @return two-column grid panel
     */
    private static JPanel twoCallouts(String a1, String v1, String a2, String v2) {
        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.add(statTile(a1, v1, TypeTrisTheme.SECONDARY));
        row.add(statTile(a2, v2, TypeTrisTheme.PRIMARY));
        capInstructionWidth(row);
        return row;
    }

    /**
     * Single stat tile with top accent stripe.
     *
     * @param label small caption
     * @param value prominent value text
     * @param accent stripe color
     * @return tile panel
     */
    private static JPanel statTile(String label, String value, Color accent) {
        JPanel t = new JPanel();
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.setOpaque(true);
        t.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        t.setBorder(
                BorderFactory.createCompoundBorder(
                        new MatteTopBorder(2, accent),
                        new EmptyBorder(10, 12, 10, 12)));
        JLabel l = new JLabel(label);
        l.setFont(TypeTrisTheme.label(9f, Font.BOLD));
        l.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        JLabel v = new JLabel(value);
        v.setFont(TypeTrisTheme.headline(16f, Font.BOLD));
        v.setForeground(TypeTrisTheme.ON_SURFACE);
        t.add(l);
        t.add(Box.createVerticalStrut(4));
        t.add(v);
        return t;
    }

    /** Swing border that draws only a horizontal line along the top edge. */
    private static final class MatteTopBorder extends javax.swing.border.AbstractBorder {
        private final int thickness;
        private final Color color;

        /**
         * @param thickness line height in pixels
         * @param color line color
         */
        MatteTopBorder(int thickness, Color color) {
            this.thickness = thickness;
            this.color = color;
        }

        /** {@inheritDoc} */
        @Override
        public void paintBorder(
                java.awt.Component c,
                java.awt.Graphics g,
                int x,
                int y,
                int width,
                int height) {
            g.setColor(color);
            g.fillRect(x, y, width, thickness);
        }

        /** {@inheritDoc} */
        @Override
        public Insets getBorderInsets(java.awt.Component c) {
            return new Insets(thickness, 0, 0, 0);
        }
    }

    /**
     * Three-column row of mini stats; middle column highlighted.
     *
     * @param l1 label 1
     * @param v1 value 1
     * @param l2 label 2
     * @param v2 value 2
     * @param l3 label 3
     * @param v3 value 3
     * @return grid panel
     */
    private static JPanel threeStats(String l1, String v1, String l2, String v2, String l3, String v3) {
        JPanel row = new JPanel(new GridLayout(1, 3, 8, 0));
        row.setOpaque(false);
        row.add(miniStat(l1, v1, false));
        row.add(miniStat(l2, v2, true));
        row.add(miniStat(l3, v3, false));
        capInstructionWidth(row);
        return row;
    }

    /**
     * Compact stat cell; optional primary top stripe when {@code highlight} is true.
     *
     * @param label caption
     * @param value value text
     * @param highlight when true, draw accent top border and tint label
     * @return panel
     */
    private static JPanel miniStat(String label, String value, boolean highlight) {
        JPanel t = new JPanel();
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.setOpaque(true);
        t.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        t.setBorder(new EmptyBorder(10, 10, 10, 10));
        if (highlight) {
            t.setBorder(
                    BorderFactory.createCompoundBorder(
                            new MatteTopBorder(2, TypeTrisTheme.PRIMARY), new EmptyBorder(10, 10, 10, 10)));
        }
        JLabel l = new JLabel(label);
        l.setFont(TypeTrisTheme.label(9f, Font.BOLD));
        l.setForeground(highlight ? TypeTrisTheme.PRIMARY : TypeTrisTheme.ON_SURFACE_VARIANT);
        JLabel v = new JLabel(value);
        v.setFont(TypeTrisTheme.headline(18f, Font.BOLD));
        v.setForeground(TypeTrisTheme.PRIMARY);
        t.add(l);
        t.add(v);
        return t;
    }

    /**
     * Full-width row: glyph cell + title + body (used for power-ups and
     * scoring—same chrome everywhere).
     *
     * @param glyph single-character or short icon text
     * @param accent stripe and glyph color
     * @param title heading
     * @param blurb body copy
     */
    private static JPanel stripCard(String glyph, Color accent, String title, String blurb) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(true);
        row.setBackground(TypeTrisTheme.SURFACE_CONTAINER);
        row.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1),
                        BorderFactory.createCompoundBorder(
                                new MatteTopBorder(3, accent),
                                new EmptyBorder(10, 12, 12, 12))));

        JPanel glyphBox = new JPanel(new BorderLayout());
        glyphBox.setPreferredSize(new Dimension(44, 44));
        glyphBox.setMinimumSize(new Dimension(44, 44));
        glyphBox.setMaximumSize(new Dimension(44, 44));
        glyphBox.setOpaque(true);
        glyphBox.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        glyphBox.setBorder(new LineBorder(accent, 1));
        JLabel g = new JLabel(glyph, SwingConstants.CENTER);
        g.setFont(TypeTrisTheme.headline(16f, Font.BOLD));
        g.setForeground(accent);
        glyphBox.add(g, BorderLayout.CENTER);

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        JLabel h = new JLabel(title);
        h.setFont(TypeTrisTheme.label(10f, Font.BOLD));
        h.setForeground(TypeTrisTheme.ON_SURFACE);
        JTextArea t = body(blurb);
        t.setFont(TypeTrisTheme.body(12f, Font.PLAIN));
        t.setSize(INSTRUCTIONS_CONTENT_W - 56, Short.MAX_VALUE);
        Dimension tp = t.getPreferredSize();
        t.setPreferredSize(new Dimension(INSTRUCTIONS_CONTENT_W - 56, tp.height));
        t.setMaximumSize(new Dimension(INSTRUCTIONS_CONTENT_W - 56, Integer.MAX_VALUE));
        col.add(h);
        col.add(Box.createVerticalStrut(4));
        col.add(t);

        row.add(glyphBox, BorderLayout.WEST);
        row.add(col, BorderLayout.CENTER);
        capInstructionWidth(row);
        return row;
    }
}
