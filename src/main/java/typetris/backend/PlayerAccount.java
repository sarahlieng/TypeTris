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
         * Average accuracy as a ratio (0.0 - 1.0). The UI formats it as a percentage.
         */
        double avgAccuracy
) {}

