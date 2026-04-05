package typetris.backend;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing helpers (PBKDF2 + random salt)
 * 
 * @author CS2212 Group 26, Western University
 */
public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 65_536;
    private static final int KEY_LENGTH = 256;

    /** Prevents instantiation; use static helpers only */
    private PasswordUtil() {
    }

    /**
     * Generates a new random salt (base64)
     */
    public static String newSaltBase64() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hashes a password with PBKDF2 and a Base64 salt.
     *
     * @param password   plaintext (cleared from the spec after use)
     * @param saltBase64 salt from {@link #newSaltBase64()}
     * @return Base64-encoded derived key
     */
    public static String hashBase64(String password, String saltBase64) {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to hash password", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Compares input password against expected hash and salt.
     *
     * @param rawPassword        user entry
     * @param saltBase64         stored salt
     * @param expectedHashBase64 stored hash
     * @return {@code true} if the recomputed hash matches
     */
    public static boolean verify(String rawPassword, String saltBase64, String expectedHashBase64) {
        String actual = hashBase64(rawPassword, saltBase64);
        return constantTimeEquals(actual, expectedHashBase64);
    }

    /**
     * Constant-time string compare to reduce timing leaks when verifying hashes.
     *
     * @param a first base64 hash string
     * @param b second base64 hash string
     * @return {@code true} if both non-null, same length, and equal
     *         codepoint-for-codepoint
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
