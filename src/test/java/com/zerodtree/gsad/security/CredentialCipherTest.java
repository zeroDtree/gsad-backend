package com.zerodtree.gsad.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    private static CredentialCipher cipher() {
        return new CredentialCipher("test-encryption-key-at-least-32-characters");
    }

    @Test
    void encryptDecrypt_roundTrip() {
        CredentialCipher cipher = cipher();

        String encrypted = cipher.encrypt("ssh-secret-password");
        assertThat(encrypted).startsWith("enc:");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("ssh-secret-password");
    }

    @Test
    void decrypt_plaintext_throws() {
        assertThatThrownBy(() -> cipher().decrypt("legacy-plain"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not encrypted");
    }
}
