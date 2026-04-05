package typetris.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Design tokens aligned with the former Tailwind theme (see {@code web/assets/js/tailwind.config.js})
 * Use these for backgrounds, borders, and accent text on Swing components instead of hard-coded hex values
 *
 * @author CS2212 Group 26, Western University
 */
public final class TypeTrisTheme {

    // Surfaces (page + elevated panels)
    public static final Color SURFACE = new Color(0x0b0e14);
    public static final Color SURFACE_CONTAINER = new Color(0x161a21);
    public static final Color SURFACE_CONTAINER_LOW = new Color(0x10131a);
    public static final Color SURFACE_CONTAINER_HIGH = new Color(0x1c2028);
    public static final Color SURFACE_BRIGHT = new Color(0x282c36);

    // Brand accents (cyan primary, magenta secondary, green tertiary)
    public static final Color PRIMARY = new Color(0xc1fffe);
    public static final Color PRIMARY_CONTAINER = new Color(0x00ffff);
    public static final Color ON_PRIMARY = new Color(0x006767);
    public static final Color SECONDARY = new Color(0xff51fa);
    public static final Color TERTIARY = new Color(0x8eff71);

    // Text and outlines on dark surfaces
    public static final Color ON_SURFACE = new Color(0xecedf6);
    public static final Color ON_SURFACE_VARIANT = new Color(0xa9abb3);
    public static final Color OUTLINE_VARIANT = new Color(0x45484f);
    public static final Color OUTLINE = new Color(0x73757d);

    public static final Color ERROR = new Color(0xff6e84);
    public static final Color YELLOW_MEDIUM = new Color(0xfde047);

    /** Prevents instantiation */
    private TypeTrisTheme() {
    }

    /**
     * Headline font: geometric sans when available, otherwise a system sans-serif
     *
     * @param size point size
     * @param style {@link Font} style constant (for example {@link Font#BOLD})
     * @return a suitable headline {@link Font}
     */
    public static Font headline(float size, int style) {
        String[] candidates = { "Space Grotesk", "Segoe UI", "San Francisco", "Helvetica Neue", "Arial" };
        for (String name : candidates) {
            Font f = new Font(name, style, Math.round(size));
            if (!f.getFamily().equals(Font.DIALOG) || "Arial".equals(name)) {
                return f;
            }
        }
        return new Font(Font.SANS_SERIF, style, Math.round(size));
    }

    /**
     * Body text font
     *
     * @param size point size
     * @param style {@link Font} style constant
     * @return a suitable body {@link Font}
     */
    public static Font body(float size, int style) {
        String[] candidates = { "Manrope", "Segoe UI", "Arial" };
        for (String name : candidates) {
            Font f = new Font(name, style, Math.round(size));
            if (!f.getFamily().equals(Font.DIALOG) || "Arial".equals(name)) {
                return f;
            }
        }
        return new Font(Font.SANS_SERIF, style, Math.round(size));
    }

    /**
     * Label / UI control font
     *
     * @param size point size
     * @param style {@link Font} style constant
     * @return a suitable label {@link Font}
     */
    public static Font label(float size, int style) {
        String[] candidates = { "Plus Jakarta Sans", "Segoe UI Semibold", "Arial" };
        for (String name : candidates) {
            Font f = new Font(name, style, Math.round(size));
            if (!f.getFamily().equals(Font.DIALOG) || "Arial".equals(name)) {
                return f;
            }
        }
        return new Font(Font.SANS_SERIF, style, Math.round(size));
    }
}
