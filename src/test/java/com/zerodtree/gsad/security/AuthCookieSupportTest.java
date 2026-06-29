package com.zerodtree.gsad.security;

import com.zerodtree.gsad.config.JwtConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthCookieSupportTest {

    @Mock
    private JwtConfig jwtConfig;

    @InjectMocks
    private AuthCookieSupport authCookieSupport;

    @Mock
    private HttpServletRequest request;

    @Test
    void isSecureRequest_whenRequestIsSecure() {
        when(request.isSecure()).thenReturn(true);

        assertTrue(authCookieSupport.isSecureRequest(request));
    }

    @Test
    void isSecureRequest_whenForwardedProtoIsHttps() {
        when(request.isSecure()).thenReturn(false);
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");

        assertTrue(authCookieSupport.isSecureRequest(request));
    }

    @Test
    void isSecureRequest_whenForwardedProtoIsHttp() {
        when(request.isSecure()).thenReturn(false);
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("http");

        assertFalse(authCookieSupport.isSecureRequest(request));
    }
}
