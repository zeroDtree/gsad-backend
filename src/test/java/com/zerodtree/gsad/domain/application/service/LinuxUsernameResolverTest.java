package com.zerodtree.gsad.domain.application.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.user.persistence.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinuxUsernameResolverTest {

    private final LinuxUsernameResolver resolver = new LinuxUsernameResolver();

    @Test
    void resolve_returnsConfiguredUsername() {
        User user = new User();
        user.setLinuxUsername("u2024012345");

        assertThat(resolver.resolve(user)).isEqualTo("u2024012345");
    }

    @Test
    void resolve_blankUsername_throws() {
        User user = new User();

        assertThatThrownBy(() -> resolver.resolve(user))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void validateAndReturn_invalidPattern_throws() {
        assertThatThrownBy(() -> resolver.validateAndReturn("1bad"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
    }
}
