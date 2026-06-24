package com.zerodtree.gsad.security;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class PasswordFingerprint {

    private PasswordFingerprint() {}

    public static String fingerprint(String storedPasswordHash) {
        if (!StringUtils.hasText(storedPasswordHash)) {
            throw new IllegalArgumentException("storedPasswordHash is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(storedPasswordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fingerprint password hash", ex);
        }
    }

    public static boolean matches(String claimedFingerprint, String storedPasswordHash) {
        if (!StringUtils.hasText(claimedFingerprint) || !StringUtils.hasText(storedPasswordHash)) {
            return false;
        }
        String expected = fingerprint(storedPasswordHash);
        return MessageDigest.isEqual(
                claimedFingerprint.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
