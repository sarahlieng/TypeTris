package typetris.backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
        String sql = """
                CREATE TABLE IF NOT EXISTS users (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  email TEXT NOT NULL UNIQUE,
                  username TEXT NOT NULL UNIQUE,
                  password_hash TEXT NOT NULL,
                  salt TEXT NOT NULL,
                  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """;
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
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
     * Simple immutable credentials row.
     */
    public record UserCredentials(String username, String passwordHash, String salt) {
    }
}
