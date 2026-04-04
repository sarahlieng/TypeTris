package typetris.gameplay;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core typing + Tetris rules ported from {@code web/assets/js/gameplay.js}.
 * <p>
 * One active {@link Piece} falls on a timer; the player types its word to unlock movement/rotation, then
 * locks it to the grid, clears full rows, and advances score / streak multipliers until top-out.
 */
public final class GameplayModel {

    public static final int COLS = 10;
    public static final int ROWS = 16;
    /** Run “level” in the HUD steps every this many line clears. */
    public static final int LINES_PER_LEVEL = 5;

    public interface Listener {
        void onRepaint();

        void onHudChanged();

        /** Called once when the run ends; stats are readable via getters. */
        void onGameOver();
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

    public static Params difficultyParams(String tier, int subLevel) {
        String t = tier == null ? "easy" : tier.toLowerCase();
        int lv = Math.max(1, Math.min(3, subLevel));
        double fallBase = switch (t) {
            case "medium" -> 0.75;
            case "hard" -> 1.25;
            default -> 0.4;
        };
        double fallRowsPerSec = fallBase * (0.9 + lv * 0.2);
        int lenMin = switch (t) {
            case "medium" -> 5;
            case "hard" -> 6;
            default -> 3;
        };
        int lenMax = switch (t) {
            case "medium", "hard" -> 5;
            default -> 4;
        };
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

    // runtime score

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
    private int mult;
    private int correctStrokes;
    private int wrongStrokes;

    private long typingClockMs;
    private long typingClockLastWallMs;

    private double fallAccum;
    private Piece piece;

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
        // Do not call spawnPiece / fireHud here: Swing view assigns "model = new GameplayModel(...)"
        // and listener callbacks must run only after that assignment completes.
    }

    /** First spawn + HUD; call once after the UI holds a reference to this model. */
    public void startRound() {
        if (over) {
            return;
        }
        spawnPiece();
        fireHud();
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isOver() {
        return over;
    }

    public Piece getPiece() {
        return piece;
    }

    public LockedCell[][] getGrid() {
        return grid;
    }

    public int getScore() {
        return score;
    }

    public int getBestDisplayed() {
        return Math.max(best, score);
    }

    public int getLinesCleared() {
        return linesCleared;
    }

    public int getLineStreak() {
        return lineStreak;
    }

    public int getMult() {
        return mult;
    }

    public int runLevelFromLines() {
        return 1 + linesCleared / LINES_PER_LEVEL;
    }

    public int levelProgressPercent() {
        int into = linesCleared % LINES_PER_LEVEL;
        return (int) Math.min(100, (into * 100L) / LINES_PER_LEVEL);
    }

    public int accuracyPercent() {
        int t = correctStrokes + wrongStrokes;
        if (t == 0) {
            return 100;
        }
        return (int) Math.round((100.0 * correctStrokes) / t);
    }

    public int wpmOverall() {
        double elapsedSec = typingClockMs / 1000.0;
        if (elapsedSec < 0.25 || correctStrokes == 0) {
            return 0;
        }
        return (int) Math.round((correctStrokes / 5.0) / (elapsedSec / 60.0));
    }

    public int wpmInstant() {
        if (!canTypeNow() || piece == null) {
            return 0;
        }
        long elapsedMs = typingClockMs - piece.typingSegmentStartClockMs;
        double elapsedSec = elapsedMs / 1000.0;
        int correctInSegment = correctStrokes - piece.correctStrokesAtSegmentStart;
        if (elapsedSec < 0.25 || correctInSegment <= 0) {
            return 0;
        }
        return (int) Math.round((correctInSegment / 5.0) / (elapsedSec / 60.0));
    }

    public void togglePause() {
        if (over) {
            return;
        }
        setPaused(!paused);
    }

    public void setPaused(boolean p) {
        paused = p;
        typingClockLastWallMs = nowMs();
        listener.onRepaint();
    }

    private boolean canTypeNow() {
        return piece != null && !over && !paused && !piece.unlocked;
    }

    private void updateTypingClock() {
        long now = nowMs();
        long last = typingClockLastWallMs;
        if (canTypeNow()) {
            typingClockMs += (now - last);
        }
        typingClockLastWallMs = now;
    }

    // simulation step which effects the gravity and falling of the pieces

    public void tick(double dt) {
        updateTypingClock();
        if (over || paused || piece == null) {
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

    private static int multFromStreak(int s) {
        return Math.min(5, 1 + s / 2);
    }

    // typing phase (word must be complete before movement keys apply)

    public void onTypingFieldChanged(String rawUppercase) {
        updateTypingClock();
        if (paused || over || piece == null || piece.unlocked) {
            return;
        }
        StringBuilder filtered = new StringBuilder();
        for (int i = 0; i < rawUppercase.length(); i++) {
            char c = rawUppercase.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                filtered.append(c);
            }
        }
        String raw = filtered.toString();
        String target = piece.word;
        int i = 0;
        while (i < raw.length() && i < target.length() && raw.charAt(i) == target.charAt(i)) {
            i++;
        }
        if (raw.length() > i) {
            Integer prevWrong = piece.lastWrongIndex;
            wrongStrokes++;
            piece.lastWrongIndex = i;
            raw = raw.substring(0, i);
        }
        int gained = i - piece.lastTypedLen;
        if (gained > 0) {
            correctStrokes += gained;
            piece.lastWrongIndex = null;
        }
        piece.lastTypedLen = i;
        if (raw.equals(target)) {
            piece.unlocked = true;
            piece.lastWrongIndex = null;
            score += 5 * target.length();
            best = Math.max(best, score);
        }
        fireHud();
        listener.onRepaint();
    }

    // unlocked piece: move / rotate / soft drop

    public void onArrowLeft() {
        tryMove(-1);
    }

    public void onArrowRight() {
        tryMove(1);
    }

    public void onArrowUp() {
        tryRotate(false);
    }

    public void onArrowDown() {
        tryRotate(true);
    }

    public void onSpace() {
        if (piece != null && piece.unlocked && !over && !paused) {
            softDropToGround();
        }
    }

    private void tryMove(int dc) {
        if (piece == null || !piece.unlocked || over || paused) {
            return;
        }
        if (validParts(piece.parts, piece.col + dc, piece.row)) {
            piece.col += dc;
            listener.onRepaint();
        }
    }

    private void tryRotate(boolean ccw) {
        if (piece == null || !piece.unlocked || over || paused) {
            return;
        }
        List<Part> next = rotatePartsCw(copyParts(piece.parts));
        if (ccw) {
            next = rotatePartsCw(rotatePartsCw(next));
        }
        if (validParts(next, piece.col, piece.row)) {
            piece.parts = next;
            listener.onRepaint();
        } else if (validParts(next, piece.col - 1, piece.row)) {
            piece.col--;
            piece.parts = next;
            listener.onRepaint();
        } else if (validParts(next, piece.col + 1, piece.row)) {
            piece.col++;
            piece.parts = next;
            listener.onRepaint();
        }
    }

    private List<Part> copyParts(List<Part> parts) {
        List<Part> o = new ArrayList<>();
        for (Part p : parts) {
            o.add(p.copy());
        }
        return o;
    }

    private void softDropToGround() {
        if (piece == null || over || paused) {
            return;
        }
        while (validParts(piece.parts, piece.col, piece.row + 1)) {
            piece.row++;
        }
        lockFromGround();
    }

    // --- Lock piece to grid, clear lines, spawn next / game over ---

    private void lockFromGround() {
        updateTypingClock();
        if (piece == null || over) {
            return;
        }
        mergePiece();
        piece = null;
        clearFullLines();
        listener.onRepaint();
        if (checkTopOut()) {
            gameOverSequence();
            return;
        }
        fireHud();
        spawnPiece();
    }

    private void mergePiece() {
        Piece pc = piece;
        if (pc == null) {
            return;
        }
        for (Part pt : pc.parts) {
            int r = pc.row + pt.y;
            int c = pc.col + pt.x;
            if (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
                grid[r][c] = new LockedCell(pt.ch);
            }
        }
    }

    private boolean checkTopOut() {
        for (int c = 0; c < COLS; c++) {
            if (grid[0][c] != null) {
                return true;
            }
        }
        return false;
    }

    /** Scan bottom-up for full rows, collapse with {@link System.arraycopy}, then score with streak multiplier. */
    private void clearFullLines() {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] == null) {
                    full = false;
                    break;
                }
            }
            if (full) {
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(grid[rr - 1], 0, grid[rr], 0, COLS);
                }
                for (int c = 0; c < COLS; c++) {
                    grid[0][c] = null;
                }
                cleared++;
                r++;
            }
        }
        if (cleared > 0) {
            int oldMult = mult;
            lineStreak++;
            mult = multFromStreak(lineStreak);
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
        if (over) {
            return;
        }
        over = true;
        piece = null;
        listener.onRepaint();
        fireHud();
        listener.onGameOver();
    }

    /** New random word shaped as a tetromino-style piece; immediate collision triggers game over. */
    private void spawnPiece() {
        updateTypingClock();
        String w = pickWord(params.lenMin(), params.lenMax());
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
        listener.onHudChanged();
    }

    private String pickWord(int lenMin, int lenMax) {
        List<String> pool = new ArrayList<>();
        for (String w : wordList) {
            if (w.length() >= lenMin && w.length() <= lenMax) {
                pool.add(w);
            }
        }
        if (pool.isEmpty()) {
            return "TYPE";
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private boolean validParts(List<Part> parts, int col, int row) {
        for (Part pt : parts) {
            int r = row + pt.y;
            int c = col + pt.x;
            if (c < 0 || c >= COLS || r >= ROWS) {
                return false;
            }
            if (r >= 0 && grid[r][c] != null) {
                return false;
            }
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
        int maxX = 0;
        int maxY = 0;
        for (Part p : parts) {
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        return new int[] { maxX + 1, maxY + 1 };
    }

    private record Cell(int x, int y) {}

    private List<Part> rotatePartsCw(List<Part> parts) {
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
        if (n <= 0) {
            return List.of();
        }
        List<List<Cell>> tetrominoes = List.of(
                List.of(c(0, 0), c(1, 0), c(2, 0), c(3, 0)),
                List.of(c(0, 0), c(1, 0), c(0, 1), c(1, 1)),
                List.of(c(1, 0), c(0, 1), c(1, 1), c(2, 1)),
                List.of(c(0, 0), c(0, 1), c(1, 1), c(2, 1)),
                List.of(c(2, 0), c(0, 1), c(1, 1), c(2, 1)),
                List.of(c(1, 0), c(2, 0), c(0, 1), c(1, 1)),
                List.of(c(0, 0), c(1, 0), c(1, 1), c(2, 1))
        );
        List<Cell> base = new ArrayList<>(tetrominoes.get(ThreadLocalRandom.current().nextInt(tetrominoes.size())));
        if (n <= 4) {
            List<Cell> subset = carveConnectedSubset(base, n);
            return normalize(subset);
        }
        Cell[] dirs = { c(1, 0), c(-1, 0), c(0, 1), c(0, -1) };
        Cell dir = dirs[ThreadLocalRandom.current().nextInt(4)];
        int extreme = Integer.MIN_VALUE;
        for (Cell b : base) {
            extreme = Math.max(extreme, b.x * dir.x + b.y * dir.y);
        }
        List<Cell> anchorCandidates = new ArrayList<>();
        for (Cell b : base) {
            if (b.x * dir.x + b.y * dir.y == extreme) {
                anchorCandidates.add(b);
            }
        }
        Cell anchor = anchorCandidates.get(ThreadLocalRandom.current().nextInt(anchorCandidates.size()));
        List<Cell> cells = new ArrayList<>(base);
        int tailLen = n - 4;
        for (int k = 1; k <= tailLen; k++) {
            cells.add(c(anchor.x + dir.x * k, anchor.y + dir.y * k));
        }
        return normalize(cells);
    }

    private static Cell c(int x, int y) {
        return new Cell(x, y);
    }

    private List<Cell> normalize(List<Cell> cells) {
        int minX = cells.stream().mapToInt(c -> c.x).min().orElse(0);
        int minY = cells.stream().mapToInt(c -> c.y).min().orElse(0);
        List<Cell> out = new ArrayList<>();
        for (Cell c : cells) {
            out.add(c(c.x - minX, c.y - minY));
        }
        return out;
    }

    private List<Cell> carveConnectedSubset(List<Cell> tetCells, int want) {
        if (want == 4) {
            return new ArrayList<>(tetCells);
        }
        Map<String, Cell> tetSet = new HashMap<>();
        for (Cell c : tetCells) {
            tetSet.put(c.x + "," + c.y, c);
        }
        Cell[] dirs = { c(1, 0), c(-1, 0), c(0, 1), c(0, -1) };
        int attempts = 200;
        var rand = ThreadLocalRandom.current();
        while (attempts-- > 0) {
            Cell start = tetCells.get(rand.nextInt(tetCells.size()));
            List<Cell> subset = new ArrayList<>();
            subset.add(start);
            Map<String, Boolean> inSubset = new HashMap<>();
            inSubset.put(start.x + "," + start.y, true);
            while (subset.size() < want) {
                List<Cell> frontier = new ArrayList<>();
                for (Cell cell : subset) {
                    for (int d = 0; d < 4; d++) {
                        int nx = cell.x + dirs[d].x;
                        int ny = cell.y + dirs[d].y;
                        String k = nx + "," + ny;
                        if (tetSet.containsKey(k) && !Boolean.TRUE.equals(inSubset.get(k))) {
                            frontier.add(c(nx, ny));
                        }
                    }
                }
                if (frontier.isEmpty()) {
                    break;
                }
                Cell pick = frontier.get(rand.nextInt(frontier.size()));
                inSubset.put(pick.x + "," + pick.y, true);
                subset.add(pick);
            }
            if (subset.size() == want) {
                return subset;
            }
        }
        return new ArrayList<>(tetCells.subList(0, want));
    }

    public String controlPhaseLabel() {
        if (piece == null) {
            return "—";
        }
        if (!piece.unlocked) {
            return "Type the word";
        }
        return "Place the block";
    }

    /** Text that should appear in the typing field (correct prefix of the target word). */
    public String currentTypingDisplay() {
        if (piece == null || piece.unlocked) {
            return "";
        }
        return piece.word.substring(0, Math.min(piece.lastTypedLen, piece.word.length()));
    }

    public String tier() {
        return tier;
    }

    public int subLevel() {
        return subLevel;
    }
}
