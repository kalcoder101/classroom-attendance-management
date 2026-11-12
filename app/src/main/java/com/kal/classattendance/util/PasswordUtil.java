package com.kal.classattendance.util;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordUtil {

    private static final int ITERATIONS = 50000;
    private static final int KEY_BITS = 160;
    private static final int SALT_BYTES = 16;

    // A new random salt, as text so it fits in a TEXT column
    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    // password + salt -> hash text
    public static String hash(String password, String saltText) {
        try {
            byte[] salt = Base64.decode(saltText, Base64.NO_WRAP);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }

    // Does this password, with this salt, give the stored hash?
    public static boolean matches(String password, String saltText, String storedHash) {
        byte[] attempt = hash(password, saltText).getBytes(StandardCharsets.UTF_8);
        byte[] stored = storedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(attempt, stored);   // compares without leaking timing
    }
}