package com.zerodtree.gsad.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerodtree.gsad.common.ErrorCode;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAuthFilterTest {

    @Mock
    private AgentCredentialService agentCredentialService;

    @Mock
    private FilterChain filterChain;

    private AgentAuthFilter agentAuthFilter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        agentAuthFilter = new AgentAuthFilter(agentCredentialService, objectMapper);
    }

    @Test
    void validCredentials_setsServerIdAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/internal/servers/report");
        request.addHeader(AgentAuthFilter.AGENT_SERVER_ID_HEADER, "gpu-01");
        request.addHeader(AgentAuthFilter.AGENT_PSK_HEADER, "derived-psk");

        when(agentCredentialService.matches("gpu-01", "derived-psk")).thenReturn(true);

        agentAuthFilter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(request.getAttribute(AgentAuthAttributes.SERVER_ID)).isEqualTo("gpu-01");
        verify(filterChain).doFilter(eq(request), any());
    }

    @Test
    void missingHeaders_returnsUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/internal/servers/report");
        MockHttpServletResponse response = new MockHttpServletResponse();

        agentAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(ErrorCode.UNAUTHORIZED.name());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void invalidPsk_returnsUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/internal/servers/report");
        request.addHeader(AgentAuthFilter.AGENT_SERVER_ID_HEADER, "gpu-01");
        request.addHeader(AgentAuthFilter.AGENT_PSK_HEADER, "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(agentCredentialService.matches("gpu-01", "wrong")).thenReturn(false);

        agentAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }
}
