package typetris.backend;

/**
 * Minimal player account view used by the statistics and leaderboard UI.
 *
 * <p>Note: this is intentionally lightweight; the project design may later expand it
 * to include sessions and additional performance metrics.
 */
public record PlayerAccount(
        String username,
        int highScore,
        int bestWPM,
        /**
         * Rolling average accuracy as a percentage (0–100), updated after each recorded run.
         */
        double avgAccuracy
) {}

