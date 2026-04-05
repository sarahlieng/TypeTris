package com.mycompany.studenttesting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * JUnit 5 tests for Type-Tris ({@code group26} / {@code typetris}).
 * Mix of black-box (public API outcomes) and white-box (boundaries, reflection for private rules).
 * Swing-heavy screens are covered with light construction / headless-safe checks only.
 */
class StudentTest {

    @BeforeAll
    static void setUpClass() {
        System.setProperty("java.awt.headless", "true");
    }

    // ── shared reflection helpers (GameplayModel line-clear rules) ─────────────────────────────

    private static void setIntField(GameplayModel target, String name, int value) throws Exception {
        Field f = GameplayModel.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setInt(target, value);
    }

    private static void setLongField(GameplayModel target, String name, long value) throws Exception {
        Field f = GameplayModel.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setLong(target, value);
    }

    private static void invokeClearFullLines(GameplayModel m) throws Exception {
        Method meth = GameplayModel.class.getDeclaredMethod("clearFullLines");
        meth.setAccessible(true);
        meth.invoke(m);
    }

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

    @Nested
    @DisplayName("App")
    class AppTests {

        @Test
        void mainMethodExists() throws Exception {
            assertNotNull(App.class.getMethod("main", String[].class));
            assertTrue(Modifier.isFinal(App.class.getModifiers()));
        }
    }

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
            // Must differ from target's second letter when present, or "TA" can match "TAB" and no mistake fires
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

    @Nested
    @DisplayName("ClientPrefs")
    class ClientPrefsTests {

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
