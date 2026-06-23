package com.zerodtree.gsad.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@Slf4j
public class CredentialCipher {

    private static final String PREFIX = "enc:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int MIN_KEY_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKey secretKey;

    public CredentialCipher(@Value("${credentials.encryption-key}") String configuredKey) {
        if (!StringUtils.hasText(configuredKey) || configuredKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "CREDENTIALS_ENCRYPTION_KEY must be set and at least 32 characters");
        }
        this.secretKey = deriveKey(configuredKey);
    }

    @PostConstruct
    void logReady() {
        log.debug("Credential encryption enabled");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt credential", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Credential value is not encrypted");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[payload.length - GCM_IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(payload, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt credential", ex);
        }
    }

    private static SecretKey deriveKey(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to derive credential encryption key", ex);
        }
    }
}
