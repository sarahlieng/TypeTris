package typetris.ui;

import java.awt.Cursor;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.border.LineBorder;

/**
 * Shared button chrome so login, stats, instructions, admin, etc. match.
 */
public final class UiButtonStyles {

    private UiButtonStyles() {
    }

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

    /** Outlined cyan — secondary actions, menus rows, “Back”, “Previous”, toolbars. */
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

    /** Same visual as {@link #applySecondary}; use for every “Back” label. */
    public static void applyBack(JButton b) {
        applySecondary(b);
    }

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
