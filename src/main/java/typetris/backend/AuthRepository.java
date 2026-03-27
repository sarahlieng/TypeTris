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
     */
    public void init() throws SQLException {
        String userSql = """
                CREATE TABLE IF NOT EXISTS users (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  email TEXT NOT NULL UNIQUE,
                  username TEXT NOT NULL UNIQUE,
                  password_hash TEXT NOT NULL,
                  salt TEXT NOT NULL,
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """;
        String sessionSql = """
                CREATE TABLE IF NOT EXISTS session_history (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  username TEXT NOT NULL,
                  started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """;
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement userPs = c.prepareStatement(userSql);
             PreparedStatement sessionPs = c.prepareStatement(sessionSql)) {
            userPs.execute();
            sessionPs.execute();
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
     * Records a new session start for a user.
     */
    public void recordSessionStart(String username) throws SQLException {
        String sql = "INSERT INTO session_history(username) VALUES(?)";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        }
    }

    /**
     * Returns all session history records ordered newest-first.
     */
    public List<SessionHistory> listSessionHistory() throws SQLException {
        String sql = "SELECT id, username, started_at FROM session_history ORDER BY started_at DESC, id DESC";
        List<SessionHistory> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new SessionHistory(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("started_at")));
            }
        }
        return out;
    }

    /**
     * Returns all user accounts ordered by username.
     */
    public List<UserAccount> listUserAccounts() throws SQLException {
        String sql = """
                SELECT u.id, u.email, u.username, u.created_at,
                       COUNT(s.id) AS session_count,
                       MAX(s.started_at) AS last_session_at
                FROM users u
                LEFT JOIN session_history s ON s.username = u.username
                GROUP BY u.id, u.email, u.username, u.created_at
                ORDER BY LOWER(u.username) ASC
                """;
        List<UserAccount> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new UserAccount(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("username"),
                        rs.getString("created_at"),
                        rs.getInt("session_count"),
                        rs.getString("last_session_at")));
            }
        }
        return out;
    }

    /**
     * Deletes an account by username.
     */
    public boolean deleteUserByUsername(String username) throws SQLException {
        String sql = "DELETE FROM users WHERE username = ?";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Resets account password hash+salt for username.
     */
    public boolean resetPasswordByUsername(String username, String passwordHash, String salt) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, salt = ? WHERE username = ?";
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setString(2, salt);
            ps.setString(3, username);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Simple immutable credentials row.
     */
    public record UserCredentials(String username, String passwordHash, String salt) {
    }

    /**
     * Immutable session history row.
     */
    public record SessionHistory(long id, String username, String startedAt) {
    }

    /**
     * Immutable user account row with session stats.
     */
    public record UserAccount(
            long id,
            String email,
            String username,
            String createdAt,
            int sessionCount,
            String lastSessionAt) {
    }
}
