package typetris.backend;

/**
 * End-of-run metrics sent to {@link AuthRepository#recordGameResult(String, CompletedRunStats)}
 *
 * @param score final score for the run
 * @param wpmOverall WPM averaged over the whole run
 * @param accuracyPercent accuracy as a percentage (0–100)
 * @param wrongStrokes incorrect keystrokes counted this run
 * @param activePlayTimeMs active play time in milliseconds (pause excluded)
 * @param maxLevelThisRun highest level reached from line clears
 * @param wordsTypedThisRun words fully typed and unlocked
 * @param peakWpmThisRun peak WPM (instant or overall) observed this run
 *
 * @author CS2212 Group 26, Western University
 */
public record CompletedRunStats(
        int score,
        int wpmOverall,
        double accuracyPercent,
        int wrongStrokes,
        long activePlayTimeMs,
        int maxLevelThisRun,
        int wordsTypedThisRun,
        int peakWpmThisRun
) {}
