package com.zerodtree.gsad.security;

import com.zerodtree.gsad.domain.user.model.UserStatus;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeUser_setsAuthenticationFromDbRoles() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(AuthCookieSupport.COOKIE_NAME, "valid-token"));

        User user = new User();
        user.setId(42L);
        user.setEmail("alice@example.com");
        user.setRoles("admin");
        user.setStatus(UserStatus.ACTIVE);

        when(jwtTokenProvider.resolveUserClaims("valid-token"))
                .thenReturn(Optional.of(new JwtUserClaims("alice@example.com", 42L, List.of("user"))));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        jwtAuthenticationFilter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
        JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) auth;
        assertThat(jwtAuth.getPrincipal()).isEqualTo("alice@example.com");
        assertThat(jwtAuth.getUserId()).isEqualTo(42L);
        assertThat(jwtAuth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        verify(filterChain).doFilter(eq(request), any());
    }

    @Test
    void inactiveUser_doesNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(AuthCookieSupport.COOKIE_NAME, "valid-token"));

        User user = new User();
        user.setId(42L);
        user.setEmail("alice@example.com");
        user.setStatus(UserStatus.INACTIVE);

        when(jwtTokenProvider.resolveUserClaims("valid-token"))
                .thenReturn(Optional.of(new JwtUserClaims("alice@example.com", 42L, List.of("admin"))));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        jwtAuthenticationFilter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingUser_doesNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(AuthCookieSupport.COOKIE_NAME, "valid-token"));

        when(jwtTokenProvider.resolveUserClaims("valid-token"))
                .thenReturn(Optional.of(new JwtUserClaims("ghost@example.com", 99L, List.of())));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        jwtAuthenticationFilter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
