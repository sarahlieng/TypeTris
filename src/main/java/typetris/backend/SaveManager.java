package typetris.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists {@link PlayerAccount} records as JSON files under {@code saves/}.
 *
 * <p>TODO: When {@code PlayerAccount} gains session history, total playtime, and other
 * aggregate stats, extend this class to read/write those fields (same directory layout).
 */
public final class SaveManager {

    /** TODO: make configurable (e.g. constructor arg or system property) if saves must live elsewhere. */
    private static final String SAVES_DIR_NAME = "saves";

    private final ObjectMapper mapper;
    private final Path savesDir;

    public SaveManager() {
        this(Path.of(SAVES_DIR_NAME));
    }

    public SaveManager(Path savesDir) {
        this.savesDir = savesDir == null ? Path.of(SAVES_DIR_NAME) : savesDir;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // Jackson 2.12+ (project uses 2.17): records are supported by jackson-databind without extra modules.
    }

    /**
     * Serializes {@code account} to {@code saves/<sanitizedUsername>.json}.
     *
     * <p>TODO: When the account model includes per-session data, write that nested structure here too.
     *
     * @return {@code true} if the account was written successfully
     */
    public boolean saveAccount(PlayerAccount account) {
        if (account == null || account.username() == null || account.username().isBlank()) {
            System.err.println("SaveManager.saveAccount: missing username; not saving.");
            return false;
        }
        try {
            Files.createDirectories(savesDir);
            Path file = saveFilePath(account.username());
            mapper.writeValue(file.toFile(), account);
            return true;
        } catch (IOException e) {
            System.err.println("SaveManager.saveAccount: failed to write save file: " + e.getMessage());
            e.printStackTrace(System.err);
            return false;
        }
    }

    /**
     * Loads {@code saves/<sanitizedUsername>.json} into a {@link PlayerAccount}.
     *
     * <p>TODO: When {@link PlayerAccount} includes sessions / playtime, deserialize those fields here.
     *
     * @return the account, or {@code null} if the file does not exist or could not be read
     */
    public PlayerAccount loadAccount(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        Path file = saveFilePath(username);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return mapper.readValue(file.toFile(), PlayerAccount.class);
        } catch (IOException e) {
            System.err.println("SaveManager.loadAccount: failed to read save file: " + e.getMessage());
            e.printStackTrace(System.err);
            return null;
        }
    }

    /**
     * Returns whether {@code saves/<sanitizedUsername>.json} exists.
     *
     * <p>TODO: If save format versioning is added later, consider existence + validity checks here.
     */
    public boolean accountExists(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return Files.isRegularFile(saveFilePath(username));
    }

    private Path saveFilePath(String username) {
        return savesDir.resolve(sanitizeUsernameForFilename(username) + ".json");
    }

    private static String sanitizeUsernameForFilename(String username) {
        String trimmed = username.trim();
        if (trimmed.isEmpty()) {
            return "unknown";
        }
        return trimmed.replaceAll("\\s+", "_");
    }
}
