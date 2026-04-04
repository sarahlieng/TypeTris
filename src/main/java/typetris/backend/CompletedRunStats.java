package typetris.backend;

/**
 * End-of-run metrics sent to {@link AuthRepository#recordGameResult(String, CompletedRunStats)}.
 */
public record CompletedRunStats(
        int score,
        /** WPM averaged over the whole run (same basis as in-game overall WPM). */
        int wpmOverall,
        double accuracyPercent,
        int wrongStrokes,
        /** Active play time for this run (pause excluded). */
        long activePlayTimeMs,
        /** Highest run level reached (from line clears). */
        int maxLevelThisRun,
        /** Words fully typed and unlocked. */
        int wordsTypedThisRun,
        /** Peak WPM observed during the run (instant or overall). */
        int peakWpmThisRun
) {}
