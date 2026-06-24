package com.zerodtree.gsad.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.zerodtree.gsad.domain.user.model.UserStatus;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            jwtTokenProvider.resolveUserClaims(token).flatMap(claims -> {
                if (claims.userId() == null || !StringUtils.hasText(claims.passwordFingerprint())) {
                    return java.util.Optional.<User>empty();
                }
                return userRepository.findById(claims.userId())
                        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                        .filter(user -> PasswordFingerprint.matches(
                                claims.passwordFingerprint(), user.getPassword()));
            }).ifPresent(user -> {
                        List<SimpleGrantedAuthority> authorities =
                                buildAuthorities(AuthorityUtils.parseRoles(user.getRoles()));
                        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                                user.getEmail(), user.getId(), authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieSupport.COOKIE_NAME.equals(cookie.getName())
                    && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private List<SimpleGrantedAuthority> buildAuthorities(List<String> roles) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                .forEach(authorities::add);
        return authorities;
    }
}
