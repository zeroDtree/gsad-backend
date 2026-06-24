package com.zerodtree.gsad.security;

import com.zerodtree.gsad.config.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCredentialServiceTest {

    private static final String MASTER_SECRET = "test-master-secret-at-least-32-chars";

    private AgentCredentialService agentCredentialService;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.setMasterSecret(MASTER_SECRET);
        agentCredentialService = new AgentCredentialService(properties);
    }

    @Test
    void derivePsk_isDeterministic() {
        String first = agentCredentialService.derivePsk("gpu-01");
        String second = agentCredentialService.derivePsk("gpu-01");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    void derivePsk_differsByServerId() {
        assertThat(agentCredentialService.derivePsk("gpu-01"))
                .isNotEqualTo(agentCredentialService.derivePsk("gpu-02"));
    }

    @Test
    void matches_acceptsDerivedPsk() {
        String serverId = "gpu-mock-001";
        String psk = agentCredentialService.derivePsk(serverId);

        assertThat(agentCredentialService.matches(serverId, psk)).isTrue();
    }

    @Test
    void matches_rejectsWrongPsk() {
        assertThat(agentCredentialService.matches("gpu-01", "deadbeef")).isFalse();
    }
}
