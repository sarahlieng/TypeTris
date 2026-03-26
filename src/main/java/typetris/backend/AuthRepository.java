package typetris.backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed repository for account creation and authentication.
 */
public final class AuthRepository {

    private final String jdbcUrl;

    /**
     * @param dbPath path to local sqlite file
     */
    public AuthRepository(Path dbPath) {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create data directory", e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    /**
     * Initializes table schema.
     *
     * <p>Because the project may be run multiple times against an existing local
     * SQLite DB, this method also performs a lightweight schema update for the
     * stats columns used by the dashboard UI.
     */
    public void init() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS users (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  email TEXT NOT NULL UNIQUE,
                  username TEXT NOT NULL UNIQUE,
                  password_hash TEXT NOT NULL,
                  salt TEXT NOT NULL,
                  high_score INTEGER NOT NULL DEFAULT 0,
                  best_wpm INTEGER NOT NULL DEFAULT 0,
                  avg_accuracy REAL NOT NULL DEFAULT 0.0,
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """;
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();

            // Lightweight "migration": older DB files may not have the new stats columns yet.
            ensureColumnExists(c, "users", "high_score", "high_score INTEGER NOT NULL DEFAULT 0");
            ensureColumnExists(c, "users", "best_wpm", "best_wpm INTEGER NOT NULL DEFAULT 0");
            ensureColumnExists(c, "users", "avg_accuracy", "avg_accuracy REAL NOT NULL DEFAULT 0.0");
        }
    }

    /**
     * Checks for existence of a column using SQLite PRAGMA table_info.
     */
    private static boolean tableColumnExists(Connection c, String table, String column) throws SQLException {
        try (ResultSet rs = c.createStatement().executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && name.equalsIgnoreCase(column)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds a column via ALTER TABLE if it doesn't exist (used for local DB migrations).
     */
    private static void ensureColumnExists(Connection c, String table, String column, String columnDefinition) throws SQLException {
        if (tableColumnExists(c, table, column)) {
            return;
        }
        try (var st = c.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + columnDefinition);
        }
    }

    /**
     * Inserts a user account.
     */
    public CreateUserResult createUser(String email, String username, String passwordHash, String salt) throws SQLException {
        String sql = "INSERT INTO users(email, username, password_hash, salt) VALUES(?,?,?,?)";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, username);
            ps.setString(3, passwordHash);
            ps.setString(4, salt);
            ps.executeUpdate();
            return CreateUserResult.CREATED;
        } catch (SQLException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (message.contains("users.email")) {
                return CreateUserResult.EMAIL_TAKEN;
            }
            if (message.contains("users.username")) {
                return CreateUserResult.USERNAME_TAKEN;
            }
            if (message.contains("constraint")) {
                return CreateUserResult.DUPLICATE_UNKNOWN;
            }
            throw ex;
        }
    }

    public enum CreateUserResult {
        CREATED,
        EMAIL_TAKEN,
        USERNAME_TAKEN,
        DUPLICATE_UNKNOWN
    }

    /**
     * Loads credentials by username.
     */
    public UserCredentials findByUsername(String username) throws SQLException {
        String sql = "SELECT username, password_hash, salt FROM users WHERE username = ?";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new UserCredentials(
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("salt"));
            }
        }
    }

    /**
     * Loads the player profile stats used by the UI.
     *
     * @return null if the user does not exist.
     */
    public PlayerAccount findPlayerAccount(String username) throws SQLException {
        String sql = """
                SELECT username, high_score, best_wpm, avg_accuracy
                FROM users
                WHERE username = ?
                """;
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new PlayerAccount(
                        rs.getString("username"),
                        rs.getInt("high_score"),
                        rs.getInt("best_wpm"),
                        rs.getDouble("avg_accuracy"));
            }
        }
    }

    /**
     * Loads leaderboard entries ordered by global high score.
     */
    public List<PlayerAccount> fetchLeaderboard(int limit) throws SQLException {
        // Keep query bounded even if the UI sends an invalid/large limit.
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String sql = """
                SELECT username, high_score, best_wpm, avg_accuracy
                FROM users
                ORDER BY high_score DESC
                LIMIT ?
                """;
        List<PlayerAccount> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PlayerAccount(
                            rs.getString("username"),
                            rs.getInt("high_score"),
                            rs.getInt("best_wpm"),
                            rs.getDouble("avg_accuracy")));
                }
            }
        }
        return out;
    }

    /**
     * Simple immutable credentials row.
     */
    public record UserCredentials(String username, String passwordHash, String salt) {
    }
}
