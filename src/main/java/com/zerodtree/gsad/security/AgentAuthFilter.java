package com.zerodtree.gsad.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentAuthFilter extends OncePerRequestFilter {

    static final String AGENT_PSK_HEADER = "X-Agent-PSK";
    static final String AGENT_SERVER_ID_HEADER = "X-Agent-Server-Id";

    private final AgentCredentialService agentCredentialService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String serverId = request.getHeader(AGENT_SERVER_ID_HEADER);
        String psk = request.getHeader(AGENT_PSK_HEADER);
        if (!StringUtils.hasText(serverId) || !StringUtils.hasText(psk)) {
            rejectUnauthorized(response, "Missing agent credentials");
            return;
        }
        if (!agentCredentialService.matches(serverId, psk)) {
            rejectUnauthorized(response, "Invalid agent credentials");
            return;
        }
        request.setAttribute(AgentAuthAttributes.SERVER_ID, serverId.trim());
        filterChain.doFilter(request, response);
    }

    private void rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(ErrorCode.UNAUTHORIZED, message));
    }
}
