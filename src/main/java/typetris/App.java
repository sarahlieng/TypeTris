package typetris;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Application entry point for Type-tris.
 * <p>
 * Schedules creation of the Swing GUI on the Event Dispatch Thread (EDT),
 * which is required for thread-safe Swing usage.
 *
 * @author CS2212 Group 26, Western University
 */
public final class App {

    /**
     * Prevents instantiation of the utility-style entry class.
     */
    private App() {
    }

    /**
     * Program entry point; delegates UI startup to the EDT.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::createAndShowGui);
    }

    /**
     * Constructs and shows a minimal starter window so {@code mvn exec:java}
     * verifies the project runs. Replace with {@code MainMenuFrame} when
     * the main menu is implemented.
     */
    private static void createAndShowGui() {
        JFrame frame = new JFrame("Type-tris");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(new JLabel("Type-tris — main window placeholder", SwingConstants.CENTER));
        frame.setSize(480, 320);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
