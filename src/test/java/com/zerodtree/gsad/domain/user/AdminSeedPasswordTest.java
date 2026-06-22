package com.zerodtree.gsad.domain.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps dev admin seed password in sync with
 * {@code db/migration-dev/V3__seed_admin.sql}.
 */
class AdminSeedPasswordTest {

    /** Must match the bcrypt value in V3__seed_admin.sql. */
    private static final String SEED_BCRYPT =
            "$2a$10$6WjNp1CrhQzl.YB.d.7PIeU9OypzxV8rNJ59KtztNM.WzxUX5hbB2";

    private static final String SEED_PASSWORD = "Admin@123456";

    @Test
    void seedBcrypt_matchesDocumentedAdminPassword() {
        assertThat(new BCryptPasswordEncoder().matches(SEED_PASSWORD, SEED_BCRYPT)).isTrue();
    }
}
