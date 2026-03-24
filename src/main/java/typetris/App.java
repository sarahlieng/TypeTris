package typetris;

/**
 * Application entry point for Type-tris.
 * <p>
 * The <strong>graphical menu</strong> is implemented with Tailwind in {@code web/index.html}.
 * Run that file in a browser (see {@code web/README.md}) for the intended UI.
 * <p>
 * This class remains for team members who start the JVM for backend logic or tests without Swing.
 *
 * @author CS2212 Group 26, Western University
 */
public final class App {

    private App() {
    }

    /**
     * Prints where to open the web UI. Does not start Swing.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        System.out.println("TYPE-TRIS");
        System.out.println("Open the Tailwind UI: web/index.html (see web/README.md).");
        System.out.println("Example: cd web && python3 -m http.server 8080  →  http://localhost:8080");
    }
}
