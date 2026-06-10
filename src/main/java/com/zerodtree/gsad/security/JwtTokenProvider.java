package com.zerodtree.gsad.security;

import com.zerodtree.gsad.config.JwtConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email, List<String> roles, Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getExpirationDays(), ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(email)
                .claim("roles", roles)
                .claim("userId", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    public String getEmailFromToken(String token) {
        return parseToken(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object roles = parseToken(token).get("roles");
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    public Long getUserIdFromToken(String token) {
        Object userId = parseToken(token).get("userId");
        if (userId instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    public Optional<JwtUserClaims> resolveUserClaims(String token) {
        try {
            Claims claims = parseToken(token);
            return Optional.of(new JwtUserClaims(
                    claims.getSubject(),
                    extractUserId(claims),
                    extractRoles(claims)));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Long extractUserId(Claims claims) {
        Object userId = claims.get("userId");
        if (userId instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }
}
