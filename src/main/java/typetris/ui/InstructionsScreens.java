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
 * scoring).
 */
final class InstructionsScreens {

    private static final int PAGE_COUNT = 8;
    /** Progress segments in the bottom nav: fixed size so every step matches. */
    private static final int DOT_SEGMENT_W = 24;
    private static final int DOT_SEGMENT_H = 4;
    /** Vertical space after page title (all tutorials). */
    private static final int GAP_AFTER_TITLE = 10;
    /** Space between stacked sections. */
    private static final int GAP_SECTION = 12;

    private InstructionsScreens() {
    }

    /**
     * BoxLayout stretches children oddly without a bounded max height; keep full
     * width.
     */
    private static void bindFullWidth(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension d = c.getPreferredSize();
        int h = Math.max(d.height, 32);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    static JPanel createWizard(Runnable backToMenu) {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JButton menuBack = new JButton("← Back");
        styleNavButton(menuBack, false);
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
        styleNavButton(prev, false);
        styleNavButton(next, true);

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

    private static JScrollPane wrapScroll(JPanel page) {
        page.setOpaque(false);
        JScrollPane sp = new JScrollPane(page);
        sp.setBorder(BorderFactory.createLineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1));
        sp.getViewport().setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);
        sp.setOpaque(true);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private static void styleNavButton(JButton b, boolean primary) {
        b.setFont(TypeTrisTheme.label(10f, Font.BOLD));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        if (primary) {
            b.setBackground(TypeTrisTheme.PRIMARY_CONTAINER);
            b.setForeground(TypeTrisTheme.ON_PRIMARY);
        } else {
            b.setBackground(TypeTrisTheme.SURFACE_CONTAINER_HIGH);
            b.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        }
        b.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.OUTLINE_VARIANT, 1),
                        BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        if (primary) {
            ButtonRolloverSupport.install(b, TypeTrisTheme.PRIMARY_CONTAINER);
        } else {
            ButtonRolloverSupport.install(b, TypeTrisTheme.SURFACE_CONTAINER_HIGH);
        }
    }

    private static JPanel buildPage(int i) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(12, 16, 16, 16));
        p.setBackground(TypeTrisTheme.SURFACE_CONTAINER_LOW);

        JLabel kicker = kickerLabel("TUTORIAL " + (i + 1));
        p.add(kicker);
        p.add(Box.createVerticalStrut(6));

        switch (i) {
            case 0 -> {
                p.add(titleLabel("Welcome to TypeTris!"));
                p.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                p.add(callout(
                        "THE BASICS OF TYPING",
                        "When blocks fall, words appear on them. Type the words correctly before the block lands."));
                p.add(Box.createVerticalStrut(GAP_SECTION));
                p.add(
                        body(
                                "From the main menu you can register or log in, then use Start game to pick a tier and "
                                        + "sub-level. Parent/teacher tools use PIN 2212 (course demo)."));
            }
            case 1 -> {
                p.add(titleLabel("Placing blocks"));
                p.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                p.add(body(
                        "Type the word on a falling block correctly to unlock its controls. Once you've typed the word "
                                + "accurately, you can rotate the block and move it left or right to position it on the "
                                + "board. Build complete rows and keep your stack low."));
                p.add(Box.createVerticalStrut(GAP_SECTION));
                p.add(twoCallouts("ACTION", "TYPE WORD", "ACTION", "POSITION"));
            }
            case 2 -> {
                p.add(titleLabel("Clearing lines"));
                p.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                p.add(body(
                        "Fill complete horizontal rows to clear them and increase your score. "
                                + "More lines at once means a bigger bonus. Clearing four lines at once is the classic "
                                + "\"Tetris\" moment."));
            }
            case 3 -> {
                p.add(titleLabel("Controls"));
                p.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                p.add(body(
                        "Type the word correctly to unlock block controls. Then use arrow keys: Left/Right to move, "
                                + "Up to rotate clockwise, Down to rotate counter-clockwise. Press Space to hard-drop "
                                + "and lock the piece where it lands."));
                p.add(Box.createVerticalStrut(GAP_SECTION));
                p.add(twoCallouts("Step 1", "TYPE", "Step 2", "MOVE"));
            }
            case 4 -> {
                p.add(titleLabel("Game over"));
                p.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                p.add(body(
                        "If blocks stack to the top of the board, the game ends. Type quickly and accurately to survive "
                                + "the increasing fall speed as you clear lines."));
                p.add(Box.createVerticalStrut(GAP_SECTION));
                p.add(twoCallouts("THREAT LEVEL", "CRITICAL", "OBJECTIVE", "SURVIVE"));
            }
            case 5 -> {
                p.add(titleLabel("Levels & difficulty"));
                p.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                p.add(calloutBox(
                        "Blocks fall faster and words get longer as you progress in a run. Each tier (Easy / Medium / "
                                + "Hard) and sub-level pushes your typing further."));
                p.add(Box.createVerticalStrut(GAP_SECTION));
                p.add(threeStats("BASE SPEED", "Tier set", "RUN LEVEL", "HUD ↑", "MULTIPLIER", "Streak"));
            }
            case 6 -> {
                p.add(titleLabel("Power-up blocks"));
                p.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                p.add(body(
                        "In the game, type five words in a row with zero mistakes to earn a power-up charge. "
                                + "Spend a charge from the gameplay bar (or press 1 / 2 / 3) to activate an effect."));
                p.add(Box.createVerticalStrut(GAP_SECTION));
                p.add(
                        stripCard(
                                "T",
                                TypeTrisTheme.PRIMARY,
                                "TIME FREEZE",
                                "Slows or pauses the drop briefly so you can finish typing or position the piece."));
                p.add(Box.createVerticalStrut(8));
                p.add(
                        stripCard(
                                "E",
                                TypeTrisTheme.TERTIARY,
                                "EASY WORDS",
                                "Temporarily uses shorter or simpler words so you can recover under pressure."));
                p.add(Box.createVerticalStrut(8));
                p.add(
                        stripCard(
                                "B",
                                TypeTrisTheme.ERROR,
                                "BLOCK BUSTER",
                                "Clears extra nearby cells when you lock the piece, carving a path through the stack."));
            }
            case 7 -> {
                p.add(titleLabel("Scoring"));
                p.add(Box.createVerticalStrut(GAP_AFTER_TITLE));
                p.add(calloutBox(
                        "Points for every correct word. Line-clear bonuses scale with how many rows you clear at once, "
                                + "multiplied by your current streak multiplier."));
                p.add(Box.createVerticalStrut(GAP_SECTION));
                p.add(
                        stripCard(
                                "S",
                                TypeTrisTheme.PRIMARY.darker(),
                                "SPEED BONUS",
                                "Faster play and higher tiers increase pressure; big clears reward you when you keep up."));
                p.add(Box.createVerticalStrut(8));
                p.add(
                        stripCard(
                                "A",
                                TypeTrisTheme.SECONDARY,
                                "ACCURACY & STREAK",
                                "Clean typing builds your line streak and raises the combo multiplier on the HUD."));
            }
            default -> p.add(body(""));
        }
        p.add(Box.createVerticalGlue());
        return p;
    }

    private static JLabel kickerLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(TypeTrisTheme.label(10f, Font.BOLD));
        l.setForeground(TypeTrisTheme.ON_SURFACE_VARIANT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel titleLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(TypeTrisTheme.headline(20f, Font.BOLD));
        l.setForeground(TypeTrisTheme.PRIMARY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

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
        a.setColumns(32);
        int estLines = Math.max(2, 1 + text.length() / 52);
        a.setRows(Math.min(14, estLines));
        bindFullWidth(a);
        return a;
    }

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
        bindFullWidth(wrap);
        return wrap;
    }

    private static JPanel calloutBox(String paragraph) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(true);
        wrap.setBackground(TypeTrisTheme.SURFACE_CONTAINER);
        wrap.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(TypeTrisTheme.SECONDARY, 2),
                        new EmptyBorder(14, 16, 14, 16)));
        wrap.add(body(paragraph), BorderLayout.CENTER);
        bindFullWidth(wrap);
        return wrap;
    }

    private static JPanel twoCallouts(String a1, String v1, String a2, String v2) {
        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.add(statTile(a1, v1, TypeTrisTheme.SECONDARY));
        row.add(statTile(a2, v2, TypeTrisTheme.PRIMARY));
        bindFullWidth(row);
        return row;
    }

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

    /** Top border only (Swing has no MatteTopBorder — use custom). */
    private static final class MatteTopBorder extends javax.swing.border.AbstractBorder {
        private final int thickness;
        private final Color color;

        MatteTopBorder(int thickness, Color color) {
            this.thickness = thickness;
            this.color = color;
        }

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

        @Override
        public Insets getBorderInsets(java.awt.Component c) {
            return new Insets(thickness, 0, 0, 0);
        }
    }

    private static JPanel threeStats(String l1, String v1, String l2, String v2, String l3, String v3) {
        JPanel row = new JPanel(new GridLayout(1, 3, 8, 0));
        row.setOpaque(false);
        row.add(miniStat(l1, v1, false));
        row.add(miniStat(l2, v2, true));
        row.add(miniStat(l3, v3, false));
        bindFullWidth(row);
        return row;
    }

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
        t.setRows(Math.max(2, 1 + blurb.length() / 48));
        col.add(h);
        col.add(Box.createVerticalStrut(4));
        col.add(t);

        row.add(glyphBox, BorderLayout.WEST);
        row.add(col, BorderLayout.CENTER);
        bindFullWidth(row);
        return row;
    }
}
