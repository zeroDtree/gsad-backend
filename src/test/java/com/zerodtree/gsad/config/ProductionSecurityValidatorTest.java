package com.zerodtree.gsad.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionSecurityValidatorTest {

    @Test
    void validateSecrets_rejectsPublicAgentBind() {
        JwtConfig jwtConfig = mock(JwtConfig.class);
        AgentProperties agentProperties = mock(AgentProperties.class);
        when(jwtConfig.getSecret()).thenReturn("prod-jwt-secret-with-enough-length-32");
        when(agentProperties.getMasterSecret()).thenReturn("prod-agent-master-secret-32-chars-min");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                jwtConfig, agentProperties);
        ReflectionTestUtils.setField(validator, "credentialsEncryptionKey", "prod-credentials-key-32-chars-min");
        ReflectionTestUtils.setField(validator, "backendAgentBind", "0.0.0.0");

        assertThatThrownBy(validator::validateSecrets)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BACKEND_AGENT_BIND=0.0.0.0");
    }

    @Test
    void validateSecrets_acceptsPrivateAgentBind() {
        JwtConfig jwtConfig = mock(JwtConfig.class);
        AgentProperties agentProperties = mock(AgentProperties.class);
        when(jwtConfig.getSecret()).thenReturn("prod-jwt-secret-with-enough-length-32");
        when(agentProperties.getMasterSecret()).thenReturn("prod-agent-master-secret-32-chars-min");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                jwtConfig, agentProperties);
        ReflectionTestUtils.setField(validator, "credentialsEncryptionKey", "prod-credentials-key-32-chars-min");
        ReflectionTestUtils.setField(validator, "backendAgentBind", "127.0.0.1");

        assertThatCode(validator::validateSecrets).doesNotThrowAnyException();
    }
}
