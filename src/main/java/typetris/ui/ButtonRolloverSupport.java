package typetris.ui;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 * Temporary hover / press background feedback for flat-styled {@link JButton}s (restores idle color on exit)
 */
public final class ButtonRolloverSupport {

    private static final String INSTALLED = "typetris.rolloverInstalled";

    private ButtonRolloverSupport() {
    }

    /**
     * Installs mouse listeners that brighten on hover and darken while pressed; always returns to {@code normalBg}
     * when the pointer leaves or the button is disabled
     */
    public static void install(JButton b, Color normalBg) {
        if (Boolean.TRUE.equals(b.getClientProperty(INSTALLED))) {
            return;
        }
        b.putClientProperty(INSTALLED, Boolean.TRUE);

        Color hoverBg = adjustBrightness(normalBg, 1.11f);
        Color pressBg = adjustBrightness(normalBg, 0.86f);

        MouseAdapter ma =
                new MouseAdapter() {
                    private boolean armed;

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        if (!b.isEnabled()) {
                            return;
                        }
                        if (!armed) {
                            b.setBackground(hoverBg);
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        if (!armed) {
                            b.setBackground(normalBg);
                        }
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (!b.isEnabled() || !SwingUtilities.isLeftMouseButton(e)) {
                            return;
                        }
                        armed = true;
                        b.setBackground(pressBg);
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (!SwingUtilities.isLeftMouseButton(e)) {
                            return;
                        }
                        armed = false;
                        if (!b.isEnabled()) {
                            b.setBackground(normalBg);
                            return;
                        }
                        if (b.contains(e.getPoint())) {
                            b.setBackground(hoverBg);
                        } else {
                            b.setBackground(normalBg);
                        }
                    }
                };
        b.addMouseListener(ma);
        b.addPropertyChangeListener(
                "enabled",
                ev -> b.setBackground(normalBg));
    }

    private static Color adjustBrightness(Color c, float factor) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float br = Math.min(1f, Math.max(0f, hsb[2] * factor));
        return Color.getHSBColor(hsb[0], hsb[1], br);
    }
}
