package typetris.ui;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Cross-session preferences for the Swing client (equivalent to the web app’s {@code localStorage} keys)
 * <p>
 * Stores per-user max sub-level cleared per difficulty tier (for unlock progression) and best score per
 * tier/level; usernames are sanitized for use as preference node keys
 *
 * @author CS2212 Group 26, Western University
 */
public final class ClientPrefs {

    private static final String NODE = "typetris/group26";

    /** Prevents instantiation */
    private ClientPrefs() {
    }

    /** @return the root {@link Preferences} node for this app */
    private static Preferences prefs() {
        return Preferences.userRoot().node(NODE);
    }

    /**
     * @param username player name
     * @param tier difficulty tier key
     * @return preference key for max sub-level cleared in that tier
     */
    private static String tierKey(String username, String tier) {
        return "tierMax." + safe(username) + "." + tier.toLowerCase();
    }

    /**
     * @param username player name
     * @param tier difficulty tier
     * @param level sub-level (1–3)
     * @return preference key for best score at that tier/level
     */
    private static String bestKey(String username, String tier, int level) {
        return "bestScore." + safe(username) + "." + tier.toLowerCase() + "-" + level;
    }

    /**
     * Sanitizes a username for stable preference keys
     *
     * @param u raw username (may be {@code null})
     * @return safe key fragment
     */
    private static String safe(String u) {
        return u == null ? "_" : u.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * @param username logged-in player
     * @param tier difficulty tier
     * @return highest sub-level cleared in {@code tier} (0–3)
     */
    public static int getMaxSubLevelCleared(String username, String tier) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        return Math.max(0, Math.min(3, prefs().getInt(tierKey(username, tier), 0)));
    }

    /**
     * Records that {@code subLevel} was finished; may unlock the next sub-level within the tier
     *
     * @param username logged-in player
     * @param tier difficulty tier
     * @param subLevel completed sub-level (1–3)
     */
    public static void recordSubLevelClear(String username, String tier, int subLevel) {
        if (username == null || username.isBlank()) {
            return;
        }
        int prev = getMaxSubLevelCleared(username, tier);
        int cleared = Math.max(1, Math.min(3, subLevel));
        int next = Math.max(prev, cleared);
        prefs().putInt(tierKey(username, tier), next);
    }

    /**
     * Whether the given sub-level is playable (sub-level 1 is always unlocked)
     *
     * @param username logged-in player
     * @param tier difficulty tier
     * @param subLevel sub-level to test (1–3)
     * @return {@code true} if the player may start this sub-level
     */
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

    /**
     * @param username logged-in player
     * @param tier difficulty tier
     * @param level sub-level (1–3)
     * @return stored best score, or zero if none
     */
    public static int getBestScore(String username, String tier, int level) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        return Math.max(0, prefs().getInt(bestKey(username, tier, level), 0));
    }

    /**
     * Updates the stored best score when {@code score} is higher than the previous value
     *
     * @param username logged-in player
     * @param tier difficulty tier
     * @param level sub-level (1–3)
     * @param score new score to consider
     */
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

    /** Removes per-user cached bests and tier unlock progress (for example after an admin stats reset) */
    public static void clearLocalScoreAndUnlockData() {
        try {
            Preferences p = prefs();
            for (String k : p.keys()) {
                if (k.startsWith("bestScore.") || k.startsWith("tierMax.")) {
                    p.remove(k);
                }
            }
            p.flush();
        } catch (BackingStoreException ignored) {
        }
    }
}
