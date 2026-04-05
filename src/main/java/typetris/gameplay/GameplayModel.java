package typetris.gameplay;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core typing and Tetris rules for one game session
 * <p>
 * Behavior follows the web client in {@code web/assets/js/gameplay.js}
 *
 * @author CS2212 Group 26, Western University
 */
public final class GameplayModel {

    public static final int COLS = 10;
    public static final int ROWS = 16;
    /** Run "level" in the HUD steps every this many line clears */
    public static final int LINES_PER_LEVEL = 5;
    /** Typing mistakes cost one life each; at zero lives the run ends (stack top-out also ends the run) */
    public static final int STARTING_LIVES = 5;

    /** View/audio callbacks from the model (Swing or tests implement this). */
    public interface Listener {
        /** Request a full board redraw. */
        void onRepaint();

        /** Score, WPM, lives, or phase changed — refresh HUD labels. */
        void onHudChanged();

        /** Called once when the run ends; stats are readable via getters */
        void onGameOver();

        /** Mirrors {@code ensureAudioStarted()} on typing / movement keys in {@code gameplay.js} */
        default void onAudioUserActivity() {}

        /** Piece locked to the stack. */
        default void onAudioBlockLand() {}

        /** @param clearedCount lines cleared in one lock */
        default void onAudioRowClear(int clearedCount) {}

        /** Combo multiplier increased. */
        default void onAudioPowerUp() {}

        /** Wrong keystroke on the target word. */
        default void onAudioTypingError() {}

        /** Called after a typing error when {@code livesRemaining} is still &gt; 0 (HUD / brief flash) */
        default void onLifeLost(int livesRemaining) {}
    }

    /** Loaded once from {@code /typetris/wordbank.txt}; fallback list if the resource is missing */
    private static List<String> wordList = List.of("TYPE", "WORD", "GAME");

    static {
        try (var in = GameplayModel.class.getResourceAsStream("/typetris/wordbank.txt")) {
            if (in != null) {
                String all = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                List<String> wl = new ArrayList<>();
                for (String w : all.trim().split("\\s+")) {
                    if (w.length() >= 3 && w.length() <= 10 && w.matches("[A-Za-z]+")) {
                        wl.add(w.toUpperCase());
                    }
                }
                if (!wl.isEmpty()) {
                    wordList = List.copyOf(wl);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Fall speed (rows/sec) and word length bounds for a tier + sub-level (1–3).
     *
     * @param fallRowsPerSec base gravity before per-level bonus
     * @param lenMin shortest word length from the bank
     * @param lenMax longest word length from the bank
     */
    public record Params(double fallRowsPerSec, int lenMin, int lenMax) {}

    /**
     * Fall speed and word length bounds for the chosen tier and sub-level
     *
     * @param tier difficulty name (for example easy, medium, hard)
     * @param subLevel sub-level from 1 to 3
     * @return parameters for this difficulty
     */
    public static Params difficultyParams(String tier, int subLevel) {
        String t = tier == null ? "easy" : tier.toLowerCase();
        int lv = Math.max(1, Math.min(3, subLevel));
        double fallBase = switch (t) {
            case "medium" -> 0.75;
            case "hard"   -> 1.25;
            default       -> 0.4;
        };
        // Sub-level scales fall speed: lv=1 → ×1.1, lv=2 → ×1.3, lv=3 → ×1.5
        double fallRowsPerSec = fallBase * (0.9 + lv * 0.2);

        // Base word-length window per tier (matches gameplay.js DIFFICULTY_SETTINGS)
        int lenMin = switch (t) {
            case "medium" -> 5;
            case "hard"   -> 6;
            default       -> 3;
        };
        int lenMax = switch (t) {
            case "medium" -> 5;
            case "hard"   -> 7;
            default       -> 4;
        };
        // Each sub-level adds 1 to both bounds, capped at 10
        lenMin = Math.min(10, lenMin + (lv - 1));
        lenMax = Math.min(10, lenMax + (lv - 1));
        if (lenMax < lenMin) {
            lenMax = lenMin;
        }
        return new Params(fallRowsPerSec, lenMin, lenMax);
    }

    /** One letter cell in a polyomino-shaped word piece; {@code wi} indexes into the target word */
    public static final class Part {
        /** Column offset from piece anchor */
        public int x;
        /** Row offset from piece anchor */
        public int y;
        /** Letter shown on this cell */
        public final char ch;
        /** Index into {@link Piece#word} */
        public final int wi;

        /**
         * @param x column offset
         * @param y row offset
         * @param ch displayed letter
         * @param wi index in the word
         */
        public Part(int x, int y, char ch, int wi) {
            this.x = x;
            this.y = y;
            this.ch = ch;
            this.wi = wi;
        }

        /** Returns a duplicate for rotation / collision checks without mutating the live piece */
        public Part copy() {
            return new Part(x, y, ch, wi);
        }
    }

    /** Falling word piece: absolute grid position plus typing progress and timing for WPM segments */
    public static final class Piece {
        /** Uppercase target string */
        public String word;
        /** Letter cells in piece-local coordinates */
        public List<Part> parts;
        /** Anchor column on the board */
        public int col;
        /** Anchor row on the board */
        public int row;
        /** {@code true} after the word is fully typed — player may move / drop */
        public boolean unlocked;
        /** Length of correct prefix already entered */
        public int lastTypedLen;
        /** Index of mistaken next character, or {@code null} */
        public Integer lastWrongIndex;
        /** {@link #typingClockMs} when current WPM segment started */
        public long typingSegmentStartClockMs;
        /** {@link #correctStrokes} at segment start */
        public int correctStrokesAtSegmentStart;

        /**
         * Restarts instant-WPM segment baseline.
         *
         * @param typingClockMs current {@link GameplayModel#typingClockMs}
         * @param correctStrokes current total correct keystrokes
         */
        void resetSegment(long typingClockMs, int correctStrokes) {
            typingSegmentStartClockMs = typingClockMs;
            correctStrokesAtSegmentStart = correctStrokes;
        }
    }

    /** Immutable letter stored in the grid after a piece locks. */
    public record LockedCell(char ch) {}

    // ── runtime state ────────────────────────────────────────────────────────

    private final Listener listener;
    private final Params params;
    private final String tier;
    private final int subLevel;

    private int best;
    private final LockedCell[][] grid = new LockedCell[ROWS][COLS];

    private boolean paused;
    private boolean over;
    private int score;
    private int linesCleared;
    private int lineStreak;
    private int mult = 1;
    private int correctStrokes;
    private int wrongStrokes;

    private long typingClockMs;
    private long typingClockLastWallMs;

    private double fallAccum;
    private Piece piece;

    /** Active play time this run (pause excluded), in milliseconds */
    private long sessionActiveMs;
    /** Peak of instant and overall WPM observed this run */
    private int peakWpmRun;
    /** Highest run level (from line clears) reached this run */
    private int peakLevelRun;
    /** Words fully typed and unlocked this run */
    private int wordsTypedRun;

    /** Matches instructions: Time freeze, Easy words, Block buster — earned after flawless word streaks */
    private static final int FLAWLESS_WORDS_PER_CHARGE = 5;
    private static final double TIME_FREEZE_DURATION_SEC = 4.0;
    private static final int EASY_WORD_SPAWN_COUNT = 3;
    private static final int BLOCK_BUSTER_CHEBYSHEV_RADIUS = 2;

    private boolean mistakeOnCurrentWord;
    private int flawlessWordStreak;
    private int powerupCharges;
    private double timeFreezeSecRemaining;
    private int easyWordSpawnsRemaining;
    /** Clears nearby locked cells on the next lock (piece footprint preserved) */
    private boolean blockBusterNextLock;

    private int lives = STARTING_LIVES;

    /**
     * Creates a model for one run; call {@link #startRound()} after the view stores this instance
     *
     * @param tier difficulty tier
     * @param subLevel sub-level from 1 to 3
     * @param bestFromPrefs best score to show in the HUD at start
     * @param listener callbacks for repaint, HUD, audio, and game over
     */
    public GameplayModel(String tier, int subLevel, int bestFromPrefs, Listener listener) {
        this.tier = tier == null ? "easy" : tier;
        this.subLevel = Math.max(1, Math.min(3, subLevel));
        this.params = difficultyParams(this.tier, this.subLevel);
        this.best = bestFromPrefs;
        this.listener = listener;
        this.typingClockLastWallMs = nowMs();
        // Do not call spawnPiece / fireHud here — listener callbacks must run only after the
        // UI has stored a reference to this model (see startRound()).
    }

    /** First spawn + HUD; call once after the UI holds a reference to this model */
    public void startRound() {
        if (over) {
            return;
        }
        spawnPiece(); // internally calls fireHud()
    }

    /** @return monotonic time in milliseconds for typing clocks */
    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    // ── accessors (read by the view / HUD) ───────────────────────────────────

    /** @return whether the run is paused */
    public boolean isPaused() { return paused; }
    /** @return whether the run has ended (loss or terminal state) */
    public boolean isOver()   { return over;   }
    /** @return the active falling piece, or {@code null} */
    public Piece   getPiece() { return piece;  }

    /** @return locked cell grid (null slots are empty) */
    public LockedCell[][] getGrid() { return grid; }

    /** @return current run score */
    public int getScore()        { return score;        }
    /** @return total full rows cleared this run */
    public int getLinesCleared() { return linesCleared; }
    /** @return consecutive line clears without a non-clear lock */
    public int getLineStreak()   { return lineStreak;   }
    /** @return score multiplier from streaks / combos */
    public int getMult()         { return mult;         }

    /** Live best — always at least the current run score so the HUD never shows a lower value */
    public int getBestDisplayed() { return Math.max(best, score); }

    /** @return HUD “level” derived from lines cleared and {@link #LINES_PER_LEVEL} */
    public int runLevelFromLines() {
        return 1 + linesCleared / LINES_PER_LEVEL;
    }

    /** @return 0–100 progress within the current HUD level segment */
    public int levelProgressPercent() {
        int into = linesCleared % LINES_PER_LEVEL;
        return (int) Math.min(100, (into * 100L) / LINES_PER_LEVEL);
    }

    /** @return accuracy percentage from correct vs wrong keystrokes this run */
    public int accuracyPercent() {
        int t = correctStrokes + wrongStrokes;
        if (t == 0) return 100;
        return (int) Math.round((100.0 * correctStrokes) / t);
    }

    /**
     * Overall WPM across the whole run — maps to {@code #hud-wpm-overall} in the HTML
     * (the small "Overall: X WPM" sub-label beneath the large live counter)
     */
    public int wpmOverall() {
        double elapsedSec = typingClockMs / 1000.0;
        if (elapsedSec < 0.25 || correctStrokes == 0) return 0;
        return (int) Math.round((correctStrokes / 5.0) / (elapsedSec / 60.0));
    }

    /**
     * Instant/live WPM for the current typing segment — maps to {@code #hud-wpm} in the HTML
     * (the large WPM counter shown prominently in the GAME INFO panel)
     */
    public int wpmInstant() {
        if (!canTypeNow() || piece == null) return 0;
        long elapsedMs = typingClockMs - piece.typingSegmentStartClockMs;
        double elapsedSec = elapsedMs / 1000.0;
        int correctInSegment = correctStrokes - piece.correctStrokesAtSegmentStart;
        if (elapsedSec < 0.25 || correctInSegment <= 0) return 0;
        return (int) Math.round((correctInSegment / 5.0) / (elapsedSec / 60.0));
    }

    /** @return incorrect keystrokes counted this run */
    public int getWrongStrokes() {
        return wrongStrokes;
    }

    /** @return active (non-paused) play time accumulated this run, in milliseconds */
    public long getSessionActivePlayMs() {
        return sessionActiveMs;
    }

    /** Peak WPM (max of instant vs overall) observed this run */
    public int getPeakWpmRun() {
        return peakWpmRun;
    }

    /** Highest level reached this run (from lines cleared) */
    public int getPeakLevelRun() {
        return peakLevelRun;
    }

    /** Words completed (typed and unlocked) this run */
    public int getWordsTypedRun() {
        return wordsTypedRun;
    }

    /** @return remaining lives */
    public int getLives() {
        return lives;
    }

    /** @return starting life count for this ruleset */
    public int getStartingLives() {
        return STARTING_LIVES;
    }

    /** @return spendable power-up charges earned from flawless words */
    public int getPowerupCharges() {
        return powerupCharges;
    }

    /** Progress toward the next charge (0 until the fifth flawless word in a row) */
    public int getFlawlessStreakProgress() {
        return flawlessWordStreak;
    }

    /** @return words in a row required to earn one charge */
    public int getFlawlessStreakGoal() {
        return FLAWLESS_WORDS_PER_CHARGE;
    }

    /** @return remaining time-freeze effect duration in seconds */
    public double getTimeFreezeRemainingSec() {
        return timeFreezeSecRemaining;
    }

    /** @return easy-word spawns left from the last easy-mode activation */
    public int getEasyWordSpawnsRemaining() {
        return easyWordSpawnsRemaining;
    }

    /** @return whether the next lock will trigger block-buster clearing */
    public boolean isBlockBusterArmed() {
        return blockBusterNextLock;
    }

    /**
     * Slows gravity for a few seconds (instructions: Time freeze).
     *
     * @return {@code false} if no charge or cannot use now
     */
    public boolean activateTimeFreeze() {
        if (over || paused || powerupCharges <= 0) {
            return false;
        }
        powerupCharges--;
        timeFreezeSecRemaining = Math.max(timeFreezeSecRemaining, TIME_FREEZE_DURATION_SEC);
        fireHud();
        return true;
    }

    /**
     * Next spawns use shorter words (instructions: Easy words).
     *
     * @return {@code false} if no charge or run is paused/over
     */
    public boolean activateEasyWords() {
        if (over || paused || powerupCharges <= 0) {
            return false;
        }
        powerupCharges--;
        easyWordSpawnsRemaining += EASY_WORD_SPAWN_COUNT;
        fireHud();
        return true;
    }

    /**
     * Next lock clears nearby stack cells (instructions: Block buster).
     *
     * @return {@code false} if no charge or run is paused/over
     */
    public boolean activateBlockBuster() {
        if (over || paused || powerupCharges <= 0) {
            return false;
        }
        powerupCharges--;
        blockBusterNextLock = true;
        fireHud();
        return true;
    }

    // ── pause / unpause ──────────────────────────────────────────────────────

    /** Flips paused state when the run is still active. */
    public void togglePause() {
        if (over) return;
        setPaused(!paused);
    }

    /**
     * @param p when {@code true}, freezes typing clock and shows pause UI
     */
    public void setPaused(boolean p) {
        paused = p;
        typingClockLastWallMs = nowMs(); // prevent clock drift while paused
        listener.onRepaint();
        fireHud(); // lets the view disable the typing field (mirrors input.disabled in gameplay.js)
    }

    // ── internal helpers ─────────────────────────────────────────────────────

    /** @return whether typing edits should affect the model */
    private boolean canTypeNow() {
        return piece != null && !over && !paused && !piece.unlocked;
    }

    /** Advances {@link #typingClockMs} while the player may type. */
    private void updateTypingClock() {
        long now = nowMs();
        if (canTypeNow()) {
            typingClockMs += (now - typingClockLastWallMs);
        }
        typingClockLastWallMs = now;
    }

    // ── gravity simulation ───────────────────────────────────────────────────

    /**
     * Advance the simulation by {@code dt} seconds; call at ~60 Hz from the game timer
     * Applies gravity and locks the piece when it can no longer fall
     */
    public void tick(double dt) {
        updateTypingClock();
        if (!over && !paused) {
            sessionActiveMs += Math.round(dt * 1000.0);
        }
        if (timeFreezeSecRemaining > 0) {
            timeFreezeSecRemaining = Math.max(0, timeFreezeSecRemaining - dt);
        }
        if (over || paused || piece == null) return;
        if (timeFreezeSecRemaining > 0) {
            listener.onRepaint();
            return;
        }

        double speed = fallSpeedRowsPerSec();
        fallAccum += dt * speed;
        while (fallAccum >= 1) {
            fallAccum -= 1;
            if (validParts(piece.parts, piece.col, piece.row + 1)) {
                piece.row++;
                listener.onRepaint();
            } else {
                lockFromGround();
                break;
            }
        }
    }

    /** Gravity including per-HUD-level bonus on top of tier params. */
    private double fallSpeedRowsPerSec() {
        int lv = runLevelFromLines();
        return params.fallRowsPerSec() + (lv - 1) * 0.15;
    }

    /**
     * {@code Math.min(5, 1 + Math.floor(s / 2))} — same as {@code gameplay.js} {@code multFromStreak}.
     *
     * @param s consecutive clears
     */
    private static int multFromStreak(int s) {
        return Math.min(5, 1 + Math.floorDiv(s, 2));
    }

    // ── typing phase ─────────────────────────────────────────────────────────

    /**
     * Processes text from the Swing typing field; mirrors {@code onTypingInput} in {@code gameplay.js}
     * Uppercase A–Z only, longest correct prefix, one wrong-stroke (and life loss) per bad edit, then
     * {@code lastTypedLen} / scoring / unlock when the word matches
     *
     * @param rawUppercase contents of the field (non-letters are stripped)
     */
    public void onTypingFieldChanged(String rawUppercase) {
        // Web: if (state.paused || state.over || !state.piece || state.piece.unlocked) return;
        if (paused || over || piece == null || piece.unlocked) {
            return;
        }
        listener.onAudioUserActivity();
        updateTypingClock();
        String target = piece.word;
        if (target == null || target.isEmpty()) {
            return;
        }

        StringBuilder filtered = new StringBuilder();
        for (int j = 0; j < rawUppercase.length(); j++) {
            char c = rawUppercase.charAt(j);
            if (c >= 'A' && c <= 'Z') {
                filtered.append(c);
            }
        }
        String raw = filtered.toString();

        int i = 0;
        while (i < raw.length() && i < target.length() && raw.charAt(i) == target.charAt(i)) {
            i++;
        }

        if (raw.length() > i) {
            wrongStrokes++;
            lives--;
            mistakeOnCurrentWord = true;
            flawlessWordStreak = 0;
            piece.lastWrongIndex = i;
            listener.onAudioTypingError();
            raw = raw.substring(0, i);
            if (lives <= 0) {
                fireHud();
                listener.onRepaint();
                gameOverSequence();
                return;
            }
            listener.onLifeLost(lives);
        }

        int gained = i - piece.lastTypedLen;
        if (gained > 0) {
            correctStrokes += gained;
            piece.lastWrongIndex = null;
        }
        piece.lastTypedLen = i;

        // Web sets input.value = raw, then if (raw === target) { unlock; input.value = ""; blur; }
        if (raw.equals(target)) {
            piece.unlocked = true;
            piece.lastWrongIndex = null;
            if (!mistakeOnCurrentWord) {
                flawlessWordStreak++;
                if (flawlessWordStreak >= FLAWLESS_WORDS_PER_CHARGE) {
                    flawlessWordStreak = 0;
                    powerupCharges++;
                }
            } else {
                flawlessWordStreak = 0;
            }
            wordsTypedRun++;
            score += 5 * target.length();
            best = Math.max(best, score);
        }

        fireHud();
        listener.onRepaint();
    }

    // ── movement / rotation / drop ───────────────────────────────────────────

    /** Move piece one column left (only when unlocked) */
    public void onArrowLeft()  { tryMove(-1); }

    /** Move piece one column right (only when unlocked) */
    public void onArrowRight() { tryMove(1); }

    /** Rotate piece clockwise (only when unlocked); maps to ↑ key per gameplay.html instructions */
    public void onArrowUp()    { tryRotate(false); }

    /** Rotate piece counter-clockwise (only when unlocked); maps to ↓ key per gameplay.html instructions */
    public void onArrowDown()  { tryRotate(true); }

    /** Soft-drop and lock the piece immediately (only when unlocked); maps to Space per gameplay.html */
    public void onSpace() {
        if (piece != null && piece.unlocked && !over && !paused) {
            listener.onAudioUserActivity();
            softDropToGround();
        }
    }

    /** Horizontal shift by {@code dc} columns when unlocked. */
    private void tryMove(int dc) {
        if (piece == null || !piece.unlocked || over || paused) return;
        listener.onAudioUserActivity();
        if (validParts(piece.parts, piece.col + dc, piece.row)) {
            piece.col += dc;
            listener.onRepaint();
        }
    }

    /** Rotates the unlocked piece CW or CCW with simple wall kicks. */
    private void tryRotate(boolean ccw) {
        if (piece == null || !piece.unlocked || over || paused) return;
        listener.onAudioUserActivity();
        List<Part> next = rotatePartsCw(copyParts(piece.parts));
        if (ccw) {
            // CCW = three CW rotations
            next = rotatePartsCw(rotatePartsCw(next));
        }
        // Wall-kick: try current col, then -1, then +1
        if (validParts(next, piece.col, piece.row)) {
            piece.parts = next;
        } else if (validParts(next, piece.col - 1, piece.row)) {
            piece.col--;
            piece.parts = next;
        } else if (validParts(next, piece.col + 1, piece.row)) {
            piece.col++;
            piece.parts = next;
        }
        listener.onRepaint();
    }

    /** Deep-copies part offsets for rotation trials. */
    private List<Part> copyParts(List<Part> parts) {
        List<Part> o = new ArrayList<>();
        for (Part p : parts) o.add(p.copy());
        return o;
    }

    /** Drops to lowest valid row then {@link #lockFromGround()}. */
    private void softDropToGround() {
        if (piece == null || over || paused) return;
        while (validParts(piece.parts, piece.col, piece.row + 1)) {
            piece.row++;
        }
        lockFromGround();
    }

    // ── locking, line clear, spawn ────────────────────────────────────────────

    /** Merge active piece into the grid, clear lines, spawn next piece or end the run (top-out / lives) */
    private void lockFromGround() {
        updateTypingClock();
        if (piece == null || over) return;
        listener.onAudioBlockLand();
        boolean buster = blockBusterNextLock;
        if (buster) {
            blockBusterNextLock = false;
        }
        Set<String> footprint = footprintKeysForPiece();
        mergePiece();
        if (buster) {
            applyBlockBusterBlast(footprint);
        }
        piece = null;
        clearFullLines();
        listener.onRepaint();
        if (checkTopOut()) {
            gameOverSequence();
            return;
        }
        // spawnPiece() ends with fireHud() — no extra HUD pass while piece is null (matches web flow).
        spawnPiece();
    }

    /** {@code "row,col"} keys for cells the active piece occupies. */
    private Set<String> footprintKeysForPiece() {
        Set<String> keys = new HashSet<>();
        if (piece == null) {
            return keys;
        }
        for (Part pt : piece.parts) {
            int r = piece.row + pt.y;
            int c = piece.col + pt.x;
            if (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
                keys.add(r + "," + c);
            }
        }
        return keys;
    }

    /**
     * Clears locked cells within Chebyshev radius of the piece center, excluding the merged footprint.
     *
     * @param mergedFootprint cells just written by the lock
     */
    private void applyBlockBusterBlast(Set<String> mergedFootprint) {
        if (mergedFootprint.isEmpty()) {
            return;
        }
        int minR = ROWS;
        int maxR = -1;
        int minC = COLS;
        int maxC = -1;
        for (String key : mergedFootprint) {
            int comma = key.indexOf(',');
            int r = Integer.parseInt(key.substring(0, comma));
            int c = Integer.parseInt(key.substring(comma + 1));
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
            minC = Math.min(minC, c);
            maxC = Math.max(maxC, c);
        }
        int centerR = (minR + maxR) / 2;
        int centerC = (minC + maxC) / 2;
        int r0 = BLOCK_BUSTER_CHEBYSHEV_RADIUS;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (mergedFootprint.contains(r + "," + c)) {
                    continue;
                }
                if (Math.max(Math.abs(r - centerR), Math.abs(c - centerC)) <= r0 && grid[r][c] != null) {
                    grid[r][c] = null;
                }
            }
        }
    }

    /** Writes {@link #piece} letters into {@link #grid}. */
    private void mergePiece() {
        if (piece == null) return;
        for (Part pt : piece.parts) {
            int r = piece.row + pt.y;
            int c = piece.col + pt.x;
            if (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
                grid[r][c] = new LockedCell(pt.ch);
            }
        }
    }

    /** @return {@code true} if any column is filled on row 0 */
    private boolean checkTopOut() {
        for (int c = 0; c < COLS; c++) {
            if (grid[0][c] != null) return true;
        }
        return false;
    }

    /**
     * Scan bottom-up for full rows, collapse with {@link System#arraycopy}, then score with streak
     * multiplier; resets streak to 0 if no lines were cleared this piece
     */
    private void clearFullLines() {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] == null) { full = false; break; }
            }
            if (full) {
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(grid[rr - 1], 0, grid[rr], 0, COLS);
                }
                grid[0] = new LockedCell[COLS];
                cleared++;
                r++; // re-check the row that just shifted down
            }
        }
        if (cleared > 0) {
            int oldMult = mult;
            listener.onAudioRowClear(cleared);
            lineStreak++;
            mult = multFromStreak(lineStreak);
            if (mult > oldMult) {
                listener.onAudioPowerUp();
            }
            int base = 100 * cleared * cleared;
            score += (int) (base * mult);
            linesCleared += cleared;
            best = Math.max(best, score);
        } else {
            lineStreak = 0;
            mult = 1;
        }
    }

    /** Sets {@link #over}, clears piece, notifies listener once. */
    private void gameOverSequence() {
        if (over) return;
        over = true;
        piece = null;
        listener.onRepaint();
        fireHud();
        listener.onGameOver();
    }

    /** New random word shaped as a tetromino-style piece; immediate collision triggers game over */
    private void spawnPiece() {
        updateTypingClock();
        mistakeOnCurrentWord = false;
        String w;
        if (easyWordSpawnsRemaining > 0) {
            easyWordSpawnsRemaining--;
            int cap = Math.min(4, params.lenMax());
            w = pickWord(3, Math.max(3, cap));
        } else {
            w = pickWord(params.lenMin(), params.lenMax());
        }
        List<Part> parts = wordToParts(w);
        int[] ext = partExtents(parts);
        int col = Math.max(0, (COLS - ext[0]) / 2);

        Piece pc = new Piece();
        pc.word = w;
        pc.parts = parts;
        pc.col = col;
        pc.row = 0;
        pc.unlocked = false;
        pc.lastTypedLen = 0;
        pc.lastWrongIndex = null;
        pc.resetSegment(typingClockMs, correctStrokes);

        if (!validParts(pc.parts, pc.col, pc.row)) {
            piece = null;
            gameOverSequence();
            return;
        }
        piece = pc;
        fallAccum = 0;
        listener.onRepaint();
        fireHud();
    }

    /** Recomputes run peaks and notifies HUD listener. */
    private void fireHud() {
        if (!over) {
            peakWpmRun = Math.max(peakWpmRun, Math.max(wpmInstant(), wpmOverall()));
            peakLevelRun = Math.max(peakLevelRun, runLevelFromLines());
        }
        listener.onHudChanged();
    }

    /** Random word from {@link #wordList} with length in {@code [lenMin, lenMax]}. */
    private String pickWord(int lenMin, int lenMax) {
        List<String> pool = new ArrayList<>();
        for (String w : wordList) {
            if (w.length() >= lenMin && w.length() <= lenMax) pool.add(w);
        }
        if (pool.isEmpty()) return "TYPE";
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** @return {@code false} if any part is out of bounds or overlaps locked cells */
    private boolean validParts(List<Part> parts, int col, int row) {
        for (Part pt : parts) {
            int r = row + pt.y;
            int c = col + pt.x;
            if (c < 0 || c >= COLS || r >= ROWS) return false;
            if (r >= 0 && grid[r][c] != null) return false;
        }
        return true;
    }

    /** Maps {@code word} onto a random tetromino-shaped cell set. */
    private List<Part> wordToParts(String word) {
        List<Cell> cells = generateTetrominoCells(word.length());
        cells.sort(Comparator.comparingInt((Cell c) -> c.y).thenComparingInt(c -> c.x));
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < word.length(); i++) {
            Cell c = cells.get(i);
            parts.add(new Part(c.x, c.y, word.charAt(i), i));
        }
        return parts;
    }

    /** @return {@code [width, height]} in cells for bounding box */
    private static int[] partExtents(List<Part> parts) {
        int maxX = 0, maxY = 0;
        for (Part p : parts) {
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        return new int[]{ maxX + 1, maxY + 1 };
    }

    /** Integer lattice point for polyomino construction. */
    private record Cell(int x, int y) {}

    /** 90° CW around origin, re-normalized to non-negative coordinates. */
    private List<Part> rotatePartsCw(List<Part> parts) {
        // CW rotation: (x, y) → (y, -x), then normalize to (0,0) origin
        List<Part> rotated = new ArrayList<>();
        for (Part p : parts) {
            rotated.add(new Part(p.y, -p.x, p.ch, p.wi));
        }
        int minX = rotated.stream().mapToInt(p -> p.x).min().orElse(0);
        int minY = rotated.stream().mapToInt(p -> p.y).min().orElse(0);
        List<Part> out = new ArrayList<>();
        for (Part p : rotated) {
            out.add(new Part(p.x - minX, p.y - minY, p.ch, p.wi));
        }
        return out;
    }

    /**
     * Picks a standard tetromino (or extension) shape holding {@code n} cells.
     *
     * @param n word length
     */
    private List<Cell> generateTetrominoCells(int n) {
        if (n <= 0) return List.of();
        List<List<Cell>> tetrominoes = List.of(
            List.of(c(0,0), c(1,0), c(2,0), c(3,0)),          // I
            List.of(c(0,0), c(1,0), c(0,1), c(1,1)),           // O
            List.of(c(1,0), c(0,1), c(1,1), c(2,1)),           // T
            List.of(c(0,0), c(0,1), c(1,1), c(2,1)),           // J
            List.of(c(2,0), c(0,1), c(1,1), c(2,1)),           // L
            List.of(c(1,0), c(2,0), c(0,1), c(1,1)),           // S
            List.of(c(0,0), c(1,0), c(1,1), c(2,1))            // Z
        );
        List<Cell> base = new ArrayList<>(
            tetrominoes.get(ThreadLocalRandom.current().nextInt(tetrominoes.size()))
        );

        if (n <= 4) {
            return normalize(carveConnectedSubset(base, n));
        }

        // For words longer than 4: attach a tail extending from one extreme of the tetromino
        Cell[] dirs = { c(1,0), c(-1,0), c(0,1), c(0,-1) };
        Cell dir = dirs[ThreadLocalRandom.current().nextInt(4)];
        int extreme = Integer.MIN_VALUE;
        for (Cell b : base) extreme = Math.max(extreme, b.x * dir.x + b.y * dir.y);

        List<Cell> anchorCandidates = new ArrayList<>();
        for (Cell b : base) {
            if (b.x * dir.x + b.y * dir.y == extreme) anchorCandidates.add(b);
        }
        Cell anchor = anchorCandidates.get(ThreadLocalRandom.current().nextInt(anchorCandidates.size()));
        List<Cell> cells = new ArrayList<>(base);
        for (int k = 1; k <= n - 4; k++) {
            cells.add(c(anchor.x + dir.x * k, anchor.y + dir.y * k));
        }
        return normalize(cells);
    }

    /** @return {@code new Cell(x,y)} */
    private static Cell c(int x, int y) { return new Cell(x, y); }

    /** Translates so min x and y are zero. */
    private List<Cell> normalize(List<Cell> cells) {
        int minX = cells.stream().mapToInt(c -> c.x).min().orElse(0);
        int minY = cells.stream().mapToInt(c -> c.y).min().orElse(0);
        List<Cell> out = new ArrayList<>();
        for (Cell c : cells) out.add(c(c.x - minX, c.y - minY));
        return out;
    }

    /**
     * Random connected sub-polyomino of size {@code want} inside {@code tetCells}.
     *
     * @param tetCells four cells of a standard tetromino
     * @param want desired size (for short words)
     */
    private List<Cell> carveConnectedSubset(List<Cell> tetCells, int want) {
        if (want == 4) return new ArrayList<>(tetCells);
        Map<String, Cell> tetSet = new HashMap<>();
        for (Cell c : tetCells) tetSet.put(c.x + "," + c.y, c);
        Cell[] dirs = { c(1,0), c(-1,0), c(0,1), c(0,-1) };
        var rand = ThreadLocalRandom.current();
        int attempts = 200;
        while (attempts-- > 0) {
            Cell start = tetCells.get(rand.nextInt(tetCells.size()));
            List<Cell> subset = new ArrayList<>();
            subset.add(start);
            Map<String, Boolean> inSubset = new HashMap<>();
            inSubset.put(start.x + "," + start.y, true);
            while (subset.size() < want) {
                List<Cell> frontier = new ArrayList<>();
                for (Cell cell : subset) {
                    for (Cell d : dirs) {
                        int nx = cell.x + d.x;
                        int ny = cell.y + d.y;
                        String k = nx + "," + ny;
                        if (tetSet.containsKey(k) && !Boolean.TRUE.equals(inSubset.get(k))) {
                            frontier.add(c(nx, ny));
                        }
                    }
                }
                if (frontier.isEmpty()) break;
                Cell pick = frontier.get(rand.nextInt(frontier.size()));
                inSubset.put(pick.x + "," + pick.y, true);
                subset.add(pick);
            }
            if (subset.size() == want) return subset;
        }
        return new ArrayList<>(tetCells.subList(0, want));
    }

    // ── display helpers ───────────────────────────────────────────────────────

    /**
     * Label for the CONTROLS / phase indicator in the GAME INFO panel
     * ({@code #hud-control-phase} in gameplay.html)
     */
    public String controlPhaseLabel() {
        if (piece == null) return "—";
        return piece.unlocked ? "Place the block" : "Type the word";
    }

    /**
     * Text that should appear in the typing input field — the correctly typed prefix of the
     * target word; empty when the piece is unlocked (web clears the field at that point)
     */
    public String currentTypingDisplay() {
        if (piece == null || piece.unlocked) return "";
        return piece.word.substring(0, Math.min(piece.lastTypedLen, piece.word.length()));
    }

    /**
     * The word on the currently falling piece; {@code null} when no piece is active
     * Shown below the board as {@code #falling-word-hint} in gameplay.html
     */
    public String fallingWordHint() {
        return (piece != null && !piece.unlocked) ? piece.word : "";
    }

    /** Difficulty tier string used when this run was started (for example easy / medium / hard) */
    public String tier() {
        return tier;
    }

    /** Sub-level within the tier (1–3) */
    public int subLevel() {
        return subLevel;
    }
}
