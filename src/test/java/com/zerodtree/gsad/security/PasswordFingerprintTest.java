package com.zerodtree.gsad.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordFingerprintTest {

    @Test
    void fingerprint_isDeterministic() {
        String hash = "$2a$10$abcdefghijklmnopqrstuv";

        assertThat(PasswordFingerprint.fingerprint(hash))
                .isEqualTo(PasswordFingerprint.fingerprint(hash));
    }

    @Test
    void matches_acceptsCurrentHash() {
        String hash = "$2a$10$abcdefghijklmnopqrstuv";
        String fingerprint = PasswordFingerprint.fingerprint(hash);

        assertThat(PasswordFingerprint.matches(fingerprint, hash)).isTrue();
    }

    @Test
    void matches_rejectsStaleHash() {
        String oldHash = "$2a$10$abcdefghijklmnopqrstuv";
        String newHash = "$2a$10$zyxwvutsrqponmlkjihgfedcba";
        String staleFingerprint = PasswordFingerprint.fingerprint(oldHash);

        assertThat(PasswordFingerprint.matches(staleFingerprint, newHash)).isFalse();
    }
}
