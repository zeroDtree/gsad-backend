package com.zerodtree.gsad.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.zerodtree.gsad.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentPskFilter extends OncePerRequestFilter {

    private static final String AGENT_PSK_HEADER = "X-Agent-PSK";
    private static final int MIN_PSK_LENGTH = 16;

    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;

    @jakarta.annotation.PostConstruct
    void warnIfPskTooShort() {
        String psk = agentProperties.getPsk();
        if (psk != null && psk.length() < MIN_PSK_LENGTH) {
            log.warn("Agent PSK is shorter than {} characters; use a longer value in production", MIN_PSK_LENGTH);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String psk = request.getHeader(AGENT_PSK_HEADER);
        String expectedPsk = agentProperties.getPsk();
        if (!StringUtils.hasText(psk) || !StringUtils.hasText(expectedPsk)) {
            rejectInvalidPsk(response);
            return;
        }
        if (!MessageDigest.isEqual(
                psk.getBytes(StandardCharsets.UTF_8),
                expectedPsk.getBytes(StandardCharsets.UTF_8))) {
            rejectInvalidPsk(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void rejectInvalidPsk(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(ErrorCode.UNAUTHORIZED, "Invalid agent PSK"));
    }
}
