package com.zerodtree.gsad.domain.user.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.user.api.ChangePasswordRequest;
import com.zerodtree.gsad.domain.user.api.LoginRequest;
import com.zerodtree.gsad.domain.user.model.UserStatus;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import com.zerodtree.gsad.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserPasswordService userPasswordService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserService userService;

    @Test
    void login_inactiveUser_forbidden() {
        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");
        user.setPassword("hash");
        user.setLinuxUsername("student");
        user.setStatus(UserStatus.INACTIVE);

        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThatThrownBy(() -> userService.login(new LoginRequest("student@example.com", "secret")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void changePassword_success() {
        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");
        user.setPassword("hash");
        user.setLinuxUsername("student");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles("");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtTokenProvider.generateToken("student@example.com", List.of(), 1L)).thenReturn("new-token");

        var result = userService.changePassword(
                1L, new ChangePasswordRequest("old-pass", "new-pass-123"));

        assertThat(result.token()).isEqualTo("new-token");
        assertThat(result.email()).isEqualTo("student@example.com");
        verify(userPasswordService).assertMatches(user, "old-pass");
        verify(userPasswordService).applyPassword(user, "new-pass-123");
    }

    @Test
    void changePassword_wrongCurrent_unauthorized() {
        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");
        user.setPassword("hash");
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid credentials"))
                .when(userPasswordService)
                .assertMatches(user, "wrong");

        assertThatThrownBy(() -> userService.changePassword(
                        1L, new ChangePasswordRequest("wrong", "new-pass-123")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void changePassword_sameAsCurrent_invalidArgument() {
        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");
        user.setPassword("hash");
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changePassword(
                        1L, new ChangePasswordRequest("same-pass", "same-pass")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void changePassword_inactiveUser_forbidden() {
        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");
        user.setPassword("hash");
        user.setStatus(UserStatus.INACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changePassword(
                        1L, new ChangePasswordRequest("old-pass", "new-pass-123")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
