package typetris.backend;

/**
 * Lightweight view of a row in {@code users} for statistics and leaderboard UIs
 *
 * <p>Fields match persisted aggregates updated by {@link AuthRepository#recordGameResult}
 *
 * @author CS2212 Group 26, Western University
 */
public record PlayerAccount(
        String username,
        int highScore,
        /** Best (peak) WPM achieved in any recorded run */
        int bestWPM,
        /**
         * Rolling average accuracy as a percentage (0–100), updated after each recorded run
         */
        double avgAccuracy,
        int gamesPlayed,
        /** Rolling average end-of-run WPM across recorded games */
        double avgWpm,
        int totalErrors,
        long totalPlayTimeMs,
        /** Highest run level reached in any recorded game */
        int maxLevel,
        int wordsTyped
) {

    /** Formats {@link #totalPlayTimeMs} for display (for example {@code "1h 2m"} or {@code "3:40"}) */
    public static String formatPlayTime(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long sec = ms / 1000;
        long m = sec / 60;
        long h = m / 60;
        if (h > 0) {
            return h + "h " + (m % 60) + "m";
        }
        return m + ":" + String.format("%02d", sec % 60);
    }
}
