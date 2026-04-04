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
 * Core typing + Tetris rules ported from {@code web/assets/js/gameplay.js}.
 * <p>
 * One active {@link Piece} falls on a timer; the player types its word to unlock movement/rotation, then
 * locks it to the grid, clears full rows, and advances score / streak multipliers until top-out.
 *
 * <p>HUD field mapping (matches {@code gameplay.html} element IDs):
 * <ul>
 *   <li>{@code #hud-score}            — {@link #getScore()}</li>
 *   <li>{@code #hud-best-score}       — {@link #getBestDisplayed()}</li>
 *   <li>{@code #hud-wpm}              — {@link #wpmInstant()} (the large, live WPM counter)</li>
 *   <li>{@code #hud-wpm-overall}      — {@link #wpmOverall()} (shown as "Overall: X WPM" sub-label)</li>
 *   <li>{@code #hud-control-phase}    — {@link #controlPhaseLabel()}</li>
 *   <li>{@code #hud-current-level}    — {@link #runLevelFromLines()}</li>
 *   <li>{@code #hud-lines-cleared}    — {@link #getLinesCleared()}</li>
 *   <li>{@code #hud-level-progress-fill} — {@link #levelProgressPercent()}</li>
 *   <li>{@code #hud-streak}           — {@link #getLineStreak()}</li>
 *   <li>{@code #hud-combo-multiplier} — {@link #getMult()} formatted as "xN"</li>
 *   <li>{@code #hud-accuracy}         — {@link #accuracyPercent()} formatted as "N%"</li>
 * </ul>
 */
public final class GameplayModel {

    public static final int COLS = 10;
    public static final int ROWS = 16;
    /** Run "level" in the HUD steps every this many line clears. */
    public static final int LINES_PER_LEVEL = 5;
    /** Typing mistakes cost one life each; at zero lives the run ends (stack top-out also ends the run). */
    public static final int STARTING_LIVES = 5;

    public interface Listener {
        void onRepaint();

        void onHudChanged();

        /** Called once when the run ends; stats are readable via getters. */
        void onGameOver();

        /** Mirrors {@code ensureAudioStarted()} on typing / movement keys in {@code gameplay.js}. */
        default void onAudioUserActivity() {}

        default void onAudioBlockLand() {}

        default void onAudioRowClear(int clearedCount) {}

        default void onAudioPowerUp() {}

        default void onAudioTypingError() {}

        /** Called after a typing error when {@code livesRemaining} is still &gt; 0 (HUD / brief flash). */
        default void onLifeLost(int livesRemaining) {}
    }

    /** Loaded once from {@code /typetris/wordlist.txt}; fallback list if the resource is missing. */
    private static List<String> wordList = List.of("TYPE", "WORD", "GAME");

    static {
        try (var in = GameplayModel.class.getResourceAsStream("/typetris/wordlist.txt")) {
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

    /** Fall speed (rows/sec) and word length bounds for a tier + sub-level (1–3). */
    public record Params(double fallRowsPerSec, int lenMin, int lenMax) {}

    /**
     * Mirrors the difficulty table in {@code gameplay.js}.
     * <pre>
     * Tier   | base fall | lenMin | lenMax (base)
     * -------|-----------|--------|---------------
     * easy   | 0.40      | 3      | 4
     * medium | 0.75      | 5      | 5
     * hard   | 1.25      | 6      | 7
     * </pre>
     * Each sub-level (1–3) adds 0.20 rows/sec and +1 to both length bounds.
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

    /** One letter cell in a polyomino-shaped word piece; {@code wi} indexes into the target word. */
    public static final class Part {
        public int x;
        public int y;
        public final char ch;
        public final int wi;

        public Part(int x, int y, char ch, int wi) {
            this.x = x;
            this.y = y;
            this.ch = ch;
            this.wi = wi;
        }

        public Part copy() {
            return new Part(x, y, ch, wi);
        }
    }

    /** Falling word piece: absolute grid position plus typing progress and timing for WPM segments. */
    public static final class Piece {
        public String word;
        public List<Part> parts;
        public int col;
        public int row;
        public boolean unlocked;
        public int lastTypedLen;
        public Integer lastWrongIndex;
        public long typingSegmentStartClockMs;
        public int correctStrokesAtSegmentStart;

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

    /** Active play time this run (pause excluded), in milliseconds. */
    private long sessionActiveMs;
    /** Peak of instant and overall WPM observed this run. */
    private int peakWpmRun;
    /** Highest run level (from line clears) reached this run. */
    private int peakLevelRun;
    /** Words fully typed and unlocked this run. */
    private int wordsTypedRun;

    /** Matches instructions: Time freeze, Easy words, Block buster — earned after flawless word streaks. */
    private static final int FLAWLESS_WORDS_PER_CHARGE = 5;
    private static final double TIME_FREEZE_DURATION_SEC = 4.0;
    private static final int EASY_WORD_SPAWN_COUNT = 3;
    private static final int BLOCK_BUSTER_CHEBYSHEV_RADIUS = 2;

    private boolean mistakeOnCurrentWord;
    private int flawlessWordStreak;
    private int powerupCharges;
    private double timeFreezeSecRemaining;
    private int easyWordSpawnsRemaining;
    /** Clears nearby locked cells on the next lock (piece footprint preserved). */
    private boolean blockBusterNextLock;

    private int lives = STARTING_LIVES;

    /**
     * Prepares parameters and listener wiring only. Call {@link #startRound()} after the view holds
     * {@code this} so listener methods never run while the UI field is still {@code null}.
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

    /** First spawn + HUD; call once after the UI holds a reference to this model. */
    public void startRound() {
        if (over) {
            return;
        }
        spawnPiece(); // internally calls fireHud()
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    // ── accessors (read by the view / HUD) ───────────────────────────────────

    public boolean isPaused() { return paused; }
    public boolean isOver()   { return over;   }
    public Piece   getPiece() { return piece;  }

    public LockedCell[][] getGrid() { return grid; }

    public int getScore()        { return score;        }
    public int getLinesCleared() { return linesCleared; }
    public int getLineStreak()   { return lineStreak;   }
    public int getMult()         { return mult;         }

    /** Live best — always at least the current run score so the HUD never shows a lower value. */
    public int getBestDisplayed() { return Math.max(best, score); }

    public int runLevelFromLines() {
        return 1 + linesCleared / LINES_PER_LEVEL;
    }

    public int levelProgressPercent() {
        int into = linesCleared % LINES_PER_LEVEL;
        return (int) Math.min(100, (into * 100L) / LINES_PER_LEVEL);
    }

    public int accuracyPercent() {
        int t = correctStrokes + wrongStrokes;
        if (t == 0) return 100;
        return (int) Math.round((100.0 * correctStrokes) / t);
    }

    /**
     * Overall WPM across the whole run — maps to {@code #hud-wpm-overall} in the HTML
     * (the small "Overall: X WPM" sub-label beneath the large live counter).
     */
    public int wpmOverall() {
        double elapsedSec = typingClockMs / 1000.0;
        if (elapsedSec < 0.25 || correctStrokes == 0) return 0;
        return (int) Math.round((correctStrokes / 5.0) / (elapsedSec / 60.0));
    }

    /**
     * Instant/live WPM for the current typing segment — maps to {@code #hud-wpm} in the HTML
     * (the large WPM counter shown prominently in the GAME INFO panel).
     */
    public int wpmInstant() {
        if (!canTypeNow() || piece == null) return 0;
        long elapsedMs = typingClockMs - piece.typingSegmentStartClockMs;
        double elapsedSec = elapsedMs / 1000.0;
        int correctInSegment = correctStrokes - piece.correctStrokesAtSegmentStart;
        if (elapsedSec < 0.25 || correctInSegment <= 0) return 0;
        return (int) Math.round((correctInSegment / 5.0) / (elapsedSec / 60.0));
    }

    public int getWrongStrokes() {
        return wrongStrokes;
    }

    public long getSessionActivePlayMs() {
        return sessionActiveMs;
    }

    /** Peak WPM (max of instant vs overall) observed this run. */
    public int getPeakWpmRun() {
        return peakWpmRun;
    }

    /** Highest level reached this run (from lines cleared). */
    public int getPeakLevelRun() {
        return peakLevelRun;
    }

    /** Words completed (typed and unlocked) this run. */
    public int getWordsTypedRun() {
        return wordsTypedRun;
    }

    public int getLives() {
        return lives;
    }

    public int getStartingLives() {
        return STARTING_LIVES;
    }

    public int getPowerupCharges() {
        return powerupCharges;
    }

    /** Progress toward the next charge (0 until the fifth flawless word in a row). */
    public int getFlawlessStreakProgress() {
        return flawlessWordStreak;
    }

    public int getFlawlessStreakGoal() {
        return FLAWLESS_WORDS_PER_CHARGE;
    }

    public double getTimeFreezeRemainingSec() {
        return timeFreezeSecRemaining;
    }

    public int getEasyWordSpawnsRemaining() {
        return easyWordSpawnsRemaining;
    }

    public boolean isBlockBusterArmed() {
        return blockBusterNextLock;
    }

    /**
     * Slows gravity for a few seconds (instructions: Time freeze).
     *
     * @return false if no charge or cannot use now
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

    public void togglePause() {
        if (over) return;
        setPaused(!paused);
    }

    public void setPaused(boolean p) {
        paused = p;
        typingClockLastWallMs = nowMs(); // prevent clock drift while paused
        listener.onRepaint();
        fireHud(); // lets the view disable the typing field (mirrors input.disabled in gameplay.js)
    }

    // ── internal helpers ─────────────────────────────────────────────────────

    private boolean canTypeNow() {
        return piece != null && !over && !paused && !piece.unlocked;
    }

    private void updateTypingClock() {
        long now = nowMs();
        if (canTypeNow()) {
            typingClockMs += (now - typingClockLastWallMs);
        }
        typingClockLastWallMs = now;
    }

    // ── gravity simulation ───────────────────────────────────────────────────

    /**
     * Advance the simulation by {@code dt} seconds. Call at ~60 Hz from the game timer.
     * Applies gravity and locks the piece when it can no longer fall.
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

    private double fallSpeedRowsPerSec() {
        int lv = runLevelFromLines();
        return params.fallRowsPerSec() + (lv - 1) * 0.15;
    }

    /** {@code Math.min(5, 1 + Math.floor(s / 2))} — same as {@code gameplay.js} {@code multFromStreak}. */
    private static int multFromStreak(int s) {
        return Math.min(5, 1 + Math.floorDiv(s, 2));
    }

    // ── typing phase ─────────────────────────────────────────────────────────

    /**
     * Feed the current typing-field text into the model (uppercase, A–Z only).
     * Called from the {@code DocumentListener} on every keystroke.
     * The model filters non-alpha characters itself for safety.
     */
    /**
     * Same algorithm as {@code onTypingInput} in {@code web/assets/js/gameplay.js}: uppercase A–Z only,
     * longest matching prefix, one wrong-stroke per bad edit, then {@code lastTypedLen} / score / unlock.
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

    /** Move piece one column left (only when unlocked). */
    public void onArrowLeft()  { tryMove(-1); }

    /** Move piece one column right (only when unlocked). */
    public void onArrowRight() { tryMove(1); }

    /** Rotate piece clockwise (only when unlocked). Maps to ↑ key per gameplay.html instructions. */
    public void onArrowUp()    { tryRotate(false); }

    /** Rotate piece counter-clockwise (only when unlocked). Maps to ↓ key per gameplay.html instructions. */
    public void onArrowDown()  { tryRotate(true); }

    /** Soft-drop and lock the piece immediately (only when unlocked). Maps to Space per gameplay.html. */
    public void onSpace() {
        if (piece != null && piece.unlocked && !over && !paused) {
            listener.onAudioUserActivity();
            softDropToGround();
        }
    }

    private void tryMove(int dc) {
        if (piece == null || !piece.unlocked || over || paused) return;
        listener.onAudioUserActivity();
        if (validParts(piece.parts, piece.col + dc, piece.row)) {
            piece.col += dc;
            listener.onRepaint();
        }
    }

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

    private List<Part> copyParts(List<Part> parts) {
        List<Part> o = new ArrayList<>();
        for (Part p : parts) o.add(p.copy());
        return o;
    }

    private void softDropToGround() {
        if (piece == null || over || paused) return;
        while (validParts(piece.parts, piece.col, piece.row + 1)) {
            piece.row++;
        }
        lockFromGround();
    }

    // ── locking, line clear, spawn ────────────────────────────────────────────

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

    private boolean checkTopOut() {
        for (int c = 0; c < COLS; c++) {
            if (grid[0][c] != null) return true;
        }
        return false;
    }

    /**
     * Scan bottom-up for full rows, collapse with {@link System#arraycopy}, then score with streak
     * multiplier. Resets streak to 0 if no lines were cleared this piece.
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

    private void gameOverSequence() {
        if (over) return;
        over = true;
        piece = null;
        listener.onRepaint();
        fireHud();
        listener.onGameOver();
    }

    /** New random word shaped as a tetromino-style piece; immediate collision triggers game over. */
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

    private void fireHud() {
        if (!over) {
            peakWpmRun = Math.max(peakWpmRun, Math.max(wpmInstant(), wpmOverall()));
            peakLevelRun = Math.max(peakLevelRun, runLevelFromLines());
        }
        listener.onHudChanged();
    }

    private String pickWord(int lenMin, int lenMax) {
        List<String> pool = new ArrayList<>();
        for (String w : wordList) {
            if (w.length() >= lenMin && w.length() <= lenMax) pool.add(w);
        }
        if (pool.isEmpty()) return "TYPE";
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private boolean validParts(List<Part> parts, int col, int row) {
        for (Part pt : parts) {
            int r = row + pt.y;
            int c = col + pt.x;
            if (c < 0 || c >= COLS || r >= ROWS) return false;
            if (r >= 0 && grid[r][c] != null) return false;
        }
        return true;
    }

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

    private static int[] partExtents(List<Part> parts) {
        int maxX = 0, maxY = 0;
        for (Part p : parts) {
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        return new int[]{ maxX + 1, maxY + 1 };
    }

    private record Cell(int x, int y) {}

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

    private static Cell c(int x, int y) { return new Cell(x, y); }

    private List<Cell> normalize(List<Cell> cells) {
        int minX = cells.stream().mapToInt(c -> c.x).min().orElse(0);
        int minY = cells.stream().mapToInt(c -> c.y).min().orElse(0);
        List<Cell> out = new ArrayList<>();
        for (Cell c : cells) out.add(c(c.x - minX, c.y - minY));
        return out;
    }

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
     * ({@code #hud-control-phase} in gameplay.html).
     */
    public String controlPhaseLabel() {
        if (piece == null) return "—";
        return piece.unlocked ? "Place the block" : "Type the word";
    }

    /**
     * Text that should appear in the typing input field — the correctly typed prefix of the
     * target word. Empty when the piece is unlocked (web clears the field at that point).
     */
    public String currentTypingDisplay() {
        if (piece == null || piece.unlocked) return "";
        return piece.word.substring(0, Math.min(piece.lastTypedLen, piece.word.length()));
    }

    /**
     * The word on the currently falling piece; {@code null} when no piece is active.
     * Shown below the board as {@code #falling-word-hint} in gameplay.html.
     */
    public String fallingWordHint() {
        return (piece != null && !piece.unlocked) ? piece.word : "";
    }

    public String tier()    { return tier;     }
    public int    subLevel(){ return subLevel; }
}
