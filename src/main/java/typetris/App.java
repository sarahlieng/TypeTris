package typetris;

import java.nio.file.Path;
import java.sql.SQLException;
import javax.swing.UIManager;
import typetris.backend.AuthRepository;
import typetris.ui.UserInterface;

/**
 * Application entry point for Type-Tris
 * <p>
 * Opens the Swing desktop UI and uses the local SQLite database
 *
 * @author CS2212 Group 26, Western University
 */
public final class App {

    private App() {
    }

    /**
     * Initializes persistence and launches {@link UserInterface} on the Swing EDT
     *
     * @param args unused
     */
    public static void main(String[] args) {
        // Prefer native window chrome; failures are non-fatal (Metal / Windows / GTK default).
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to default look and feel.
        }

        Path dbPath = Path.of("data", "type-tris.db");
        AuthRepository auth = new AuthRepository(dbPath);
        try {
            auth.init(); // creates tables under ./data if missing
            System.out.println("TYPE-TRIS database ready: " + dbPath.toAbsolutePath());
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            e.printStackTrace(System.err);
            return;
        }

        // All UI work must run on the Swing EDT.
        UserInterface.launch(auth);
    }
}
