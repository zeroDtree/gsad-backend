package com.zerodtree.gsad.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Authenticated principal carrying email and userId extracted from a valid JWT.
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String email;
    private final Long userId;

    public JwtAuthenticationToken(String email, Long userId,
                                  Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.email = email;
        this.userId = userId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return email;
    }

    public Long getUserId() {
        return userId;
    }
}
