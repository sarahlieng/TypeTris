package com.mycompany.studenttesting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import javax.swing.JButton;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import typetris.App;
import typetris.backend.AuthRepository;
import typetris.backend.CompletedRunStats;
import typetris.backend.Leaderboard;
import typetris.backend.PasswordUtil;
import typetris.backend.PlayerAccount;
import typetris.backend.WebServer;
import typetris.gameplay.GameplayAudio;
import typetris.gameplay.GameplayModel;
import typetris.gameplay.GameplayPanel;
import typetris.parental.ParentalControl;
import typetris.parental.TimeRange;
import typetris.ui.ButtonRolloverSupport;
import typetris.ui.ClientPrefs;
import typetris.ui.Screens;
import typetris.ui.TypeTrisTheme;
import typetris.ui.UiButtonStyles;
import typetris.ui.UserInterface;

/**
 * JUnit 5 test suite for the Type-Tris application ({@code typetris}, CS2212 group 26).
 * <p>
 * Coverage emphasises business logic and persistence: scoring, authentication, game rules,
 * parental controls, and related utilities. Full interactive Swing flows are out of scope;
 * tests use public APIs where possible, and reflection only when behaviour is not reachable
 * otherwise (for example, private line-clear logic). Lightweight checks confirm that selected
 * UI components can be constructed in headless mode.
 * <p>
 * Tests are grouped with {@code @Nested} classes by subsystem. Execute from the {@code StudentTest}
 * module with {@code mvn test}. Install the main {@code type-tris} artifact locally first
 * ({@code mvn install} from the parent project) if the dependency is not already in your Maven
 * repository.
 */
class StudentTest {

    /**
     * Enables headless AWT/Swing so tests run in environments without a graphical display
     * (for example, CI servers).
     */
    @BeforeAll
    static void setUpClass() {
        System.setProperty("java.awt.headless", "true");
    }

    // ── GameplayModel: reflection helpers for state that is not exposed publicly ────────────────

    /** Sets a private {@code int} field on the model for controlled test scenarios. */
    private static void setIntField(GameplayModel target, String name, int value) throws Exception {
        Field f = GameplayModel.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setInt(target, value);
    }

    /** Same as {@link #setIntField(GameplayModel, String, int)} for {@code long} fields (e.g. typing clock). */
    private static void setLongField(GameplayModel target, String name, long value) throws Exception {
        Field f = GameplayModel.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setLong(target, value);
    }

    /** Invokes private {@code clearFullLines} to verify scoring and grid behaviour without a full play session. */
    private static void invokeClearFullLines(GameplayModel m) throws Exception {
        Method meth = GameplayModel.class.getDeclaredMethod("clearFullLines");
        meth.setAccessible(true);
        meth.invoke(m);
    }

    /** Minimal {@link GameplayModel.Listener} implementation; tests focus on model state, not UI callbacks. */
    private static GameplayModel.Listener silentListener() {
        return new GameplayModel.Listener() {
            @Override
            public void onRepaint() {}

            @Override
            public void onHudChanged() {}

            @Override
            public void onGameOver() {}
        };
    }

    /** Verifies the application entry class exposes {@code main} and is declared {@code final}. */
    @Nested
    @DisplayName("App")
    class AppTests {

        @Test
        void mainMethodExists() throws Exception {
            assertNotNull(App.class.getMethod("main", String[].class));
            assertTrue(Modifier.isFinal(App.class.getModifiers()));
        }
    }

    /**
     * Uses an isolated SQLite database per test (temporary directory) so the production
     * {@code data/type-tris.db} file is never modified. Covers account creation, credential lookup,
     * password verification, session history, and monotonic high-score updates.
     */
    @Nested
    @DisplayName("AuthRepository & PasswordUtil")
    class AuthTests {

        @TempDir
        Path tempDir;

        private Path dbPath() {
            return tempDir.resolve("t-" + UUID.randomUUID() + ".db");
        }

        @Test
        void createUser_findByUsername_roundTrip() throws SQLException {
            AuthRepository repo = new AuthRepository(dbPath());
            repo.init();
            String u = "u_" + UUID.randomUUID().toString().substring(0, 8);
            String salt = PasswordUtil.newSaltBase64();
            String hash = PasswordUtil.hashBase64("password123", salt);
            assertEquals(AuthRepository.CreateUserResult.CREATED,
                    repo.createUser(u + "@x.test", u, hash, salt));
            AuthRepository.UserCredentials c = repo.findByUsername(u);
            assertNotNull(c);
            assertTrue(PasswordUtil.verify("password123", c.salt(), c.passwordHash()));
            assertFalse(PasswordUtil.verify("wrong", c.salt(), c.passwordHash()));
        }

        @Test
        void findByUsername_unknownReturnsNull() throws SQLException {
            AuthRepository repo = new AuthRepository(dbPath());
            repo.init();
            assertNull(repo.findByUsername("nobody_" + UUID.randomUUID()));
        }

        @Test
        void recordGameResult_highScoreNeverDecreases() throws SQLException {
            AuthRepository repo = new AuthRepository(dbPath());
            repo.init();
            String u = "p_" + UUID.randomUUID().toString().substring(0, 8);
            String salt = PasswordUtil.newSaltBase64();
            repo.createUser(u + "@x.test", u, PasswordUtil.hashBase64("pw", salt), salt);
            repo.recordGameResult(u, new CompletedRunStats(500, 40, 90.0, 1, 10_000L, 2, 5, 40));
            repo.recordGameResult(u, new CompletedRunStats(100, 30, 80.0, 2, 5_000L, 1, 1, 30));
            PlayerAccount pa = repo.findPlayerAccount(u);
            assertEquals(500, pa.highScore());
        }

        @Test
        void recordSessionStart_insertsRow() throws SQLException {
            AuthRepository repo = new AuthRepository(dbPath());
            repo.init();
            String u = "s_" + UUID.randomUUID().toString().substring(0, 8);
            repo.createUser(u + "@x.test", u, PasswordUtil.hashBase64("pw", PasswordUtil.newSaltBase64()),
                    PasswordUtil.newSaltBase64());
            repo.recordSessionStart(u);
            assertFalse(repo.listSessionHistory().isEmpty());
        }
    }

    /** Ensures {@link CompletedRunStats} record accessors match constructor arguments. */
    @Nested
    @DisplayName("CompletedRunStats")
    class CompletedRunStatsTests {

        @Test
        void accessorsMatchConstruction() {
            CompletedRunStats r = new CompletedRunStats(10, 20, 95.5, 3, 1_000L, 4, 7, 25);
            assertEquals(10, r.score());
            assertEquals(20, r.wpmOverall());
            assertEquals(95.5, r.accuracyPercent());
            assertEquals(3, r.wrongStrokes());
            assertEquals(1_000L, r.activePlayTimeMs());
            assertEquals(4, r.maxLevelThisRun());
            assertEquals(7, r.wordsTypedThisRun());
            assertEquals(25, r.peakWpmThisRun());
        }
    }

    /** Validates leaderboard ordering against persisted user scores in a real database. */
    @Nested
    @DisplayName("Leaderboard")
    class LeaderboardTests {

        @TempDir
        Path tempDir;

        @Test
        void getTopPlayers_ordersByHighScore() throws SQLException {
            Path db = tempDir.resolve("lb.db");
            AuthRepository repo = new AuthRepository(db);
            repo.init();
            String salt = PasswordUtil.newSaltBase64();
            String a = "a_" + UUID.randomUUID().toString().substring(0, 6);
            String b = "b_" + UUID.randomUUID().toString().substring(0, 6);
            repo.createUser(a + "@t.t", a, PasswordUtil.hashBase64("p", salt), salt);
            repo.createUser(b + "@t.t", b, PasswordUtil.hashBase64("p", salt), salt);
            repo.recordGameResult(a, new CompletedRunStats(100, 10, 100.0, 0, 1L, 1, 1, 10));
            repo.recordGameResult(b, new CompletedRunStats(500, 20, 100.0, 0, 1L, 1, 1, 20));

            Leaderboard lb = new Leaderboard(repo);
            List<PlayerAccount> top = lb.getTopPlayers(10);
            assertFalse(top.isEmpty());
            assertTrue(top.get(0).highScore() >= top.get(top.size() - 1).highScore());
        }
    }

    /** Tests {@link PlayerAccount#formatPlayTime(long)} and related display helpers, including edge cases. */
    @Nested
    @DisplayName("PlayerAccount")
    class PlayerAccountTests {

        @Test
        void formatPlayTime_hoursAndMinutes() {
            assertEquals("1h 2m", PlayerAccount.formatPlayTime(3_720_000L));
        }

        @Test
        void formatPlayTime_minutesSeconds() {
            assertEquals("3:05", PlayerAccount.formatPlayTime(185_000L));
        }

        @Test
        void formatPlayTime_negativeTreatedAsZero() {
            assertEquals("0:00", PlayerAccount.formatPlayTime(-1L));
        }
    }

    /** Integration smoke test: start and stop {@link WebServer} on a temporary web root and database. */
    @Nested
    @DisplayName("WebServer")
    class WebServerTests {

        @TempDir
        Path tempDir;

        @Test
        void startAndStop_bindsPort() throws Exception {
            int port;
            try (ServerSocket ss = new ServerSocket(0)) {
                port = ss.getLocalPort();
            }
            Path web = Files.createDirectories(tempDir.resolve("www"));
            Path db = tempDir.resolve("ws.db");
            WebServer ws = new WebServer(port, web, db);
            assertDoesNotThrow(() -> ws.start());
            ws.stop();
        }
    }

    /**
     * Core gameplay logic: typing input, levels, WPM calculations, and line clearing. Reflection
     * is used only where no public API exposes the behaviour under test (for example, {@code clearFullLines}).
     */
    @Nested
    @DisplayName("GameplayModel")
    class GameplayModelTests {

        @Test
        void accuracyPercent_zeroStrokesIs100() {
            GameplayModel m = new GameplayModel("easy", 1, 0, silentListener());
            m.startRound();
            assertEquals(100, m.accuracyPercent());
        }

        @Test
        void runLevelFromLines_and_levelProgressPercent() throws Exception {
            GameplayModel m = new GameplayModel("easy", 1, 0, silentListener());
            m.startRound();
            setIntField(m, "linesCleared", 4);
            assertEquals(1, m.runLevelFromLines());
            assertEquals(80, m.levelProgressPercent());
            setIntField(m, "linesCleared", 5);
            assertEquals(2, m.runLevelFromLines());
            assertEquals(0, m.levelProgressPercent());
        }

        @Test
        void wpmOverall_afterSufficientTime() throws Exception {
            GameplayModel m = new GameplayModel("easy", 1, 0, silentListener());
            m.startRound();
            setLongField(m, "typingClockMs", 1000);
            setIntField(m, "correctStrokes", 10);
            assertEquals(120, m.wpmOverall());
        }

        @Test
        void onTypingFieldChanged_unlocksOnFullWord() {
            GameplayModel m = new GameplayModel("easy", 1, 0, silentListener());
            m.startRound();
            String w = m.getPiece().word;
            m.onTypingFieldChanged(w);
            assertTrue(m.getPiece().unlocked);
        }

        @Test
        void onTypingFieldChanged_wrongLetterCostsLife() {
            GameplayModel m = new GameplayModel("easy", 1, 0, silentListener());
            m.startRound();
            String w = m.getPiece().word;
            // Wrong letter must differ from the target's second character when present, otherwise the
            // two-character input can match a valid prefix (e.g. "TA" for "TAB") and no mistake is recorded.
            char wrong = 'A';
            for (char c = 'A'; c <= 'Z'; c++) {
                if (w.length() < 2 || c != w.charAt(1)) {
                    wrong = c;
                    break;
                }
            }
            m.onTypingFieldChanged(w.substring(0, 1) + wrong);
            assertEquals(GameplayModel.STARTING_LIVES - 1, m.getLives());
        }

        @Test
        void clearFullLines_clearsFullRow_andScores() throws Exception {
            GameplayModel m = new GameplayModel("easy", 1, 0, silentListener());
            m.startRound();
            GameplayModel.LockedCell[][] g = m.getGrid();
            int bottom = GameplayModel.ROWS - 1;
            for (int c = 0; c < GameplayModel.COLS; c++) {
                g[bottom][c] = new GameplayModel.LockedCell('Z');
            }
            int before = m.getScore();
            invokeClearFullLines(m);
            assertEquals(1, m.getLinesCleared());
            assertEquals(before + 100, m.getScore());
        }

        @Test
        void difficultyParams_clampsSubLevel() {
            GameplayModel.Params a = GameplayModel.difficultyParams("easy", 0);
            GameplayModel.Params b = GameplayModel.difficultyParams("easy", 1);
            assertEquals(b.lenMin(), a.lenMin());
        }
    }

    /**
     * Headless smoke test: constructs {@link GameplayPanel} to verify initialization succeeds without
     * a display or manual interaction.
     */
    @Nested
    @DisplayName("GameplayPanel (headless smoke)")
    class GameplayPanelTests {

        @TempDir
        Path tempDir;

        @Test
        void constructor_buildsPanel() throws SQLException {
            Path db = tempDir.resolve("gp.db");
            AuthRepository auth = new AuthRepository(db);
            auth.init();
            GameplayPanel panel = new GameplayPanel(
                    auth, "player1", "easy", 1, () -> {}, s -> {});
            assertNotNull(panel);
            assertTrue(panel.isOpaque());
        }
    }

    /** Ensures {@link GameplayAudio#shutdown()} completes cleanly when background audio was never started. */
    @Nested
    @DisplayName("GameplayAudio")
    class GameplayAudioTests {

        @Test
        void shutdown_withoutStart_isSafe() {
            GameplayAudio audio = new GameplayAudio();
            audio.setBgmPaused(true);
            assertDoesNotThrow(audio::shutdown);
        }
    }

    /**
     * Parental control and {@link TimeRange}: permitted play windows, session recording, aggregates,
     * and validation of invalid inputs (inverted time ranges, negative durations, and similar cases).
     */
    @Nested
    @DisplayName("ParentalControl & TimeRange")
    class ParentalTests {

        @Test
        void timeRange_containsInclusiveBounds() {
            TimeRange r = new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0));
            assertTrue(r.contains(LocalTime.of(9, 0)));
            assertTrue(r.contains(LocalTime.of(17, 0)));
            assertFalse(r.contains(LocalTime.of(8, 59)));
        }

        @Test
        void timeRange_endBeforeStart_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TimeRange(LocalTime.of(18, 0), LocalTime.of(9, 0)));
        }

        @Test
        void parental_noWindows_alwaysAllowed() {
            ParentalControl pc = new ParentalControl();
            assertTrue(pc.isPlayAllowedAt(LocalTime.MIDNIGHT));
        }

        @Test
        void parental_windowRestricts() {
            ParentalControl pc = new ParentalControl();
            pc.addAllowedPlayWindow(new TimeRange(LocalTime.of(12, 0), LocalTime.of(13, 0)));
            assertTrue(pc.isPlayAllowedAt(LocalTime.of(12, 30)));
            assertFalse(pc.isPlayAllowedAt(LocalTime.of(11, 0)));
        }

        @Test
        void parental_recordSession_rejectsNegativeDuration() {
            ParentalControl pc = new ParentalControl();
            assertThrows(IllegalArgumentException.class,
                    () -> pc.recordSession(Duration.ofSeconds(-1)));
        }

        @Test
        void parental_recordSession_datetime_rejectsInverted() {
            ParentalControl pc = new ParentalControl();
            LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> pc.recordSession(t.plusHours(1), t));
        }

        @Test
        void parental_averageSessionTime_emptyIsZero() {
            ParentalControl pc = new ParentalControl();
            assertEquals(Duration.ZERO, pc.getAverageSessionTime());
        }

        @Test
        void parental_averageSessionTime_twoSessions() {
            ParentalControl pc = new ParentalControl();
            pc.recordSession(Duration.ofMinutes(10));
            pc.recordSession(Duration.ofMinutes(20));
            assertEquals(Duration.ofMinutes(15), pc.getAverageSessionTime());
            assertEquals(2, pc.getSessionCount());
        }
    }

    /**
     * {@link ClientPrefs} persistence for best scores and sub-level progress. Each test uses a
     * unique synthetic username to avoid cross-test interference in shared preference storage.
     */
    @Nested
    @DisplayName("ClientPrefs")
    class ClientPrefsTests {

        /** Returns a unique username for isolated preference keys per test case. */
        private String user() {
            return "cp_" + UUID.randomUUID().toString().replace("-", "");
        }

        @Test
        void putBestScoreIfHigher_strictInequality() {
            String u = user();
            ClientPrefs.putBestScoreIfHigher(u, "easy", 1, 100);
            ClientPrefs.putBestScoreIfHigher(u, "easy", 1, 50);
            assertEquals(100, ClientPrefs.getBestScore(u, "easy", 1));
            ClientPrefs.putBestScoreIfHigher(u, "easy", 1, 101);
            assertEquals(101, ClientPrefs.getBestScore(u, "easy", 1));
        }

        @Test
        void getMaxSubLevelCleared_and_record() {
            String u = user();
            assertEquals(0, ClientPrefs.getMaxSubLevelCleared(u, "medium"));
            ClientPrefs.recordSubLevelClear(u, "medium", 2);
            assertTrue(ClientPrefs.getMaxSubLevelCleared(u, "medium") >= 2);
        }

        @Test
        void isSubLevelUnlocked_blankUser_level2False() {
            assertFalse(ClientPrefs.isSubLevelUnlocked("", "easy", 2));
        }
    }

    /** Verifies {@link Screens} navigation constants used with {@code CardLayout} remain defined. */
    @Nested
    @DisplayName("Screens")
    class ScreensTests {

        @Test
        void constants_nonBlank() {
            assertEquals("menu", Screens.MENU);
            assertEquals("gameplay", Screens.GAMEPLAY);
            assertEquals("leaderboard", Screens.LEADERBOARD);
        }
    }

    /** Basic checks on {@link TypeTrisTheme} colours and font factory methods. */
    @Nested
    @DisplayName("TypeTrisTheme")
    class TypeTrisThemeTests {

        @Test
        void colors_areNonNull() {
            assertNotNull(TypeTrisTheme.PRIMARY);
            assertNotNull(TypeTrisTheme.SURFACE);
        }

        @Test
        void headline_returnsFont() {
            assertTrue(TypeTrisTheme.headline(14f, java.awt.Font.BOLD).getSize() > 0);
        }
    }

    /** Applies {@link UiButtonStyles} and {@link ButtonRolloverSupport} to a {@link JButton} in headless mode. */
    @Nested
    @DisplayName("UiButtonStyles & ButtonRolloverSupport")
    class ButtonStyleTests {

        @Test
        void applyPrimary_stylesButton() {
            JButton b = new JButton("OK");
            assertDoesNotThrow(() -> UiButtonStyles.applyPrimary(b));
            assertNotNull(b.getBackground());
        }

        @Test
        void buttonRolloverSupport_install_idempotentFlag() {
            JButton b = new JButton("x");
            assertDoesNotThrow(() -> ButtonRolloverSupport.install(b, TypeTrisTheme.SURFACE));
            assertDoesNotThrow(() -> ButtonRolloverSupport.install(b, TypeTrisTheme.SURFACE));
        }
    }

    /**
     * {@code InstructionsScreens} is package-private; reflection invokes {@code createWizard} to confirm
     * a {@link javax.swing.JPanel} is returned without error.
     */
    @Nested
    @DisplayName("InstructionsScreens (package-private)")
    class InstructionsScreensTests {

        @Test
        void createWizard_viaReflection_returnsPanel() throws Exception {
            Class<?> c = Class.forName("typetris.ui.InstructionsScreens");
            Method m = c.getDeclaredMethod("createWizard", Runnable.class);
            m.setAccessible(true);
            Object panel = m.invoke(null, (Runnable) () -> {});
            assertNotNull(panel);
            assertTrue(panel instanceof javax.swing.JPanel);
        }
    }

    /** Confirms {@link UserInterface} is public and declares {@code launch(AuthRepository)}; does not start the UI. */
    @Nested
    @DisplayName("UserInterface")
    class UserInterfaceTests {

        @Test
        void classIsPublicForEdtLauncher() throws Exception {
            assertTrue(Modifier.isPublic(UserInterface.class.getModifiers()));
            assertNotNull(UserInterface.class.getMethod("launch", AuthRepository.class));
        }
    }
}
