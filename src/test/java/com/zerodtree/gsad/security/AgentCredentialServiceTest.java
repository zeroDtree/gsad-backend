package com.zerodtree.gsad.security;

import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentCredentialServiceTest {

    @Mock
    private ServerRepository serverRepository;

    @InjectMocks
    private AgentCredentialService agentCredentialService;

    @Test
    void matches_acceptsStoredPsk() {
        Server server = new Server();
        server.setServerId("gpu-node-01");
        server.setAgentPsk("stored-psk-value1");
        when(serverRepository.findByServerId("gpu-node-01")).thenReturn(Optional.of(server));

        assertThat(agentCredentialService.matches("gpu-node-01", "stored-psk-value1")).isTrue();
    }

    @Test
    void matches_rejectsWrongPsk() {
        Server server = new Server();
        server.setServerId("gpu-node-01");
        server.setAgentPsk("stored-psk-value1");
        when(serverRepository.findByServerId("gpu-node-01")).thenReturn(Optional.of(server));

        assertThat(agentCredentialService.matches("gpu-node-01", "other-psk-value")).isFalse();
    }

    @Test
    void matches_rejectsMissingPsk() {
        Server server = new Server();
        server.setServerId("gpu-node-01");
        when(serverRepository.findByServerId("gpu-node-01")).thenReturn(Optional.of(server));

        assertThat(agentCredentialService.matches("gpu-node-01", "anything-at-all")).isFalse();
    }

    @Test
    void matches_rejectsUnknownServer() {
        when(serverRepository.findByServerId("missing")).thenReturn(Optional.empty());

        assertThat(agentCredentialService.matches("missing", "stored-psk-value1")).isFalse();
    }
}
