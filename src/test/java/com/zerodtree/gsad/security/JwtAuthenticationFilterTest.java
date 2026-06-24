package com.zerodtree.gsad.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.zerodtree.gsad.domain.user.model.UserStatus;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String CURRENT_HASH = "$2a$10$current-password-hash-value";
    private static final String STALE_HASH = "$2a$10$stale-password-hash-value-xx";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsTokenWhenPasswordFingerprintMismatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieSupport.COOKIE_NAME, "stale-token"));

        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setPassword(CURRENT_HASH);

        when(jwtTokenProvider.resolveUserClaims("stale-token"))
                .thenReturn(Optional.of(new JwtUserClaims(
                        "student@example.com",
                        1L,
                        List.of(),
                        PasswordFingerprint.fingerprint(STALE_HASH))));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> {});

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void acceptsTokenWhenPasswordFingerprintMatches() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieSupport.COOKIE_NAME, "valid-token"));

        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles("");
        user.setPassword(CURRENT_HASH);

        when(jwtTokenProvider.resolveUserClaims("valid-token"))
                .thenReturn(Optional.of(new JwtUserClaims(
                        "student@example.com",
                        1L,
                        List.of(),
                        PasswordFingerprint.fingerprint(CURRENT_HASH))));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> {});

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
