package typetris;

import java.nio.file.Path;
import typetris.backend.WebServer;

/**
 * Application entry point for Type-tris.
 * Starts local web server for frontend + auth APIs.
 *
 * @author CS2212 Group 26, Western University
 */
public final class App {

    private App() {
    }

    /**
     * Starts local server and serves /web files and /api routes.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        int port = 8080;
        Path webRoot = Path.of("web");
        Path dbPath = Path.of("data", "type-tris.db");
        WebServer server = new WebServer(port, webRoot, dbPath);

        try {
            server.start();
            System.out.println("TYPE-TRIS backend started.");
            System.out.println("Open: http://localhost:" + port);
            System.out.println("Database: " + dbPath.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to start TYPE-TRIS backend: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
