package com.zerodtree.gsad.security;

import com.zerodtree.gsad.config.JwtConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthCookieSupport {

    public static final String COOKIE_NAME = "GSAD_TOKEN";

    private final JwtConfig jwtConfig;

    public ResponseCookie createTokenCookie(String token, boolean secure) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(jwtConfig.getExpirationDays()))
                .build();
    }

    public ResponseCookie clearTokenCookie(boolean secure) {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
