package typetris.ui;

import java.util.prefs.Preferences;

/**
 * Cross-session preferences for the Swing client (equivalent to the web app’s {@code localStorage} keys).
 * <p>
 * Stores per-user max sub-level cleared per difficulty tier (for unlock progression) and best score per
 * tier/level. Usernames are sanitized for use as preference node keys.
 */
public final class ClientPrefs {

    private static final String NODE = "typetris/group26";

    private ClientPrefs() {
    }

    private static Preferences prefs() {
        return Preferences.userRoot().node(NODE);
    }

    private static String tierKey(String username, String tier) {
        return "tierMax." + safe(username) + "." + tier.toLowerCase();
    }

    private static String bestKey(String username, String tier, int level) {
        return "bestScore." + safe(username) + "." + tier.toLowerCase() + "-" + level;
    }

    /** Sanitize username for stable, filesystem-safe preference keys. */
    private static String safe(String u) {
        return u == null ? "_" : u.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static int getMaxSubLevelCleared(String username, String tier) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        return Math.max(0, Math.min(3, prefs().getInt(tierKey(username, tier), 0)));
    }

    /** Records that {@code subLevel} was finished; unlocks {@code subLevel + 1} within the tier. */
    public static void recordSubLevelClear(String username, String tier, int subLevel) {
        if (username == null || username.isBlank()) {
            return;
        }
        int prev = getMaxSubLevelCleared(username, tier);
        int cleared = Math.max(1, Math.min(3, subLevel));
        int next = Math.max(prev, cleared);
        prefs().putInt(tierKey(username, tier), next);
    }

    /** Mirrors {@code typetrisDifficultyProgress.isUnlocked} from {@code app.js}. */
    public static boolean isSubLevelUnlocked(String username, String tier, int subLevel) {
        int n = Math.max(1, Math.min(3, subLevel));
        if (n <= 1) {
            return true;
        }
        if (username == null || username.isBlank()) {
            return false;
        }
        return getMaxSubLevelCleared(username, tier) >= n - 1;
    }

    public static int getBestScore(String username, String tier, int level) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        return Math.max(0, prefs().getInt(bestKey(username, tier, level), 0));
    }

    public static void putBestScoreIfHigher(String username, String tier, int level, int score) {
        if (username == null || username.isBlank()) {
            return;
        }
        String k = bestKey(username, tier, level);
        int cur = prefs().getInt(k, 0);
        if (score > cur) {
            prefs().putInt(k, score);
        }
    }
}
