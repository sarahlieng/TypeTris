package typetris.ui;

import java.awt.Cursor;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.border.LineBorder;

/**
 * Shared button chrome so login, stats, instructions, admin, etc match
 *
 * @author CS2212 Group 26, Western University
 */
public final class UiButtonStyles {

    /** Prevents instantiation */
    private UiButtonStyles() {
    }

    /**
     * Primary (filled cyan) style for main actions
     *
     * @param b button to style
     */
    public static void applyPrimary(JButton b) {
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
     * Outlined cyan style for secondary actions (back, previous, toolbars)
     *
     * @param b button to style
     */
    public static void applySecondary(JButton b) {
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

    /**
     * Same visual as {@link #applySecondary}; use for “Back” labels
     *
     * @param b button to style
     */
    public static void applyBack(JButton b) {
        applySecondary(b);
    }

    /**
     * Muted surface style for low-emphasis buttons
     *
     * @param b button to style
     */
    public static void applyMuted(JButton b) {
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

    /**
     * Text link appearance (no border, hand cursor)
     *
     * @param b button to style
     */
    public static void applyLink(JButton b) {
        b.setFont(TypeTrisTheme.label(12f, Font.BOLD));
        b.setForeground(TypeTrisTheme.SECONDARY);
        b.setBackground(TypeTrisTheme.SURFACE_CONTAINER);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
