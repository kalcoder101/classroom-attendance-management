package com.kal.classattendance.util;

import android.os.Build;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordUtil {

    /**
     * PBKDF2 parameters, following the OWASP password-storage guidance.
     * "PBKDF2WithHmacSHA256" joined Android in API 26, so older devices fall
     * back to SHA-1 with the same (higher) iteration count.
     */
    private static final String SHA256_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String SHA1_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int ITERATIONS = 210000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    /**
     * Stored hashes are self-describing: {@code <algorithm>$<iterations>$<base64 hash>},
     * so every hash carries its own cost parameters (never recompute a different
     * one when the defaults change). Bare base64 hashes are legacy rows from
     * v1.1.0 and earlier: PBKDF2WithHmacSHA1 at 50,000 iterations and 160 bits.
     * Base64 never contains "$", so the separator is unambiguous.
     */
    private static final String SEPARATOR = "$";
    private static final String LEGACY_ALGORITHM = SHA1_ALGORITHM;
    private static final int LEGACY_ITERATIONS = 50000;
    private static final int LEGACY_KEY_BITS = 160;

    // A new random salt, as text so it fits in a TEXT column
    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    // password + salt -> self-describing hash text
    public static String hash(String password, String saltText) {
        String algorithm = currentAlgorithm();
        return algorithm + SEPARATOR + ITERATIONS + SEPARATOR
                + hashWith(algorithm, ITERATIONS, keyBitsFor(algorithm), password, saltText);
    }

    private static String currentAlgorithm() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? SHA256_ALGORITHM : SHA1_ALGORITHM;
    }

    private static int keyBitsFor(String algorithm) {
        return algorithm.endsWith("SHA256") ? KEY_BITS : LEGACY_KEY_BITS;
    }

    private static String hashWith(String algorithm, int iterations, int keyBits,
                                   String password, String saltText) {
        try {
            byte[] salt = Base64.decode(saltText, Base64.NO_WRAP);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }

    // Does this password, with this salt, give the stored hash?
    // Accepts both the current self-describing format and legacy v1.1.0 hashes.
    public static boolean matches(String password, String saltText, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        int separatorIndex = storedHash.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            // Legacy bare hash: PBKDF2WithHmacSHA1, 50,000 iterations, 160 bits
            byte[] attempt = hashWith(LEGACY_ALGORITHM, LEGACY_ITERATIONS, LEGACY_KEY_BITS,
                    password, saltText).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(attempt, storedHash.getBytes(StandardCharsets.UTF_8));
        }

        String[] parts = storedHash.split("\\$");
        if (parts.length != 3) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] attempt = hashWith(parts[0], iterations, keyBitsFor(parts[0]),
                password, saltText).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(attempt, parts[2].getBytes(StandardCharsets.UTF_8));
    }

    /**
     * true when the stored hash predates the current parameters (legacy 50k
     * SHA-1, or different defaults after an app update) and should be rehashed
     * with {@link #hash} after its next successful verification.
     */
    public static boolean needsRehash(String storedHash) {
        return storedHash == null
                || !storedHash.startsWith(currentAlgorithm() + SEPARATOR + ITERATIONS + SEPARATOR);
    }
}
