package com.zerodtree.gsad.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecuritySecretsValidatorTest {

    @Test
    void validateSecrets_rejectsAnyLocalAgentBindInProd() {
        assertAgentBindRejectedInProd("0.0.0.0", null, "BACKEND_AGENT_BIND=0.0.0.0");
    }

    @Test
    void validateSecrets_rejectsPublicAgentBindInProd() {
        assertAgentBindRejectedInProd("203.0.113.1", null, "BACKEND_AGENT_VPN_CIDRS");
    }

    @Test
    void validateSecrets_rejectsNetBirdBindWithoutVpnCidrs() {
        assertAgentBindRejectedInProd("100.67.167.35", null, "BACKEND_AGENT_VPN_CIDRS is empty");
    }

    @Test
    void validateSecrets_acceptsNetBirdBindWithVpnCidrs() {
        SecuritySecretsValidator validator = prodValidator("100.67.167.35", "100.67.0.0/16");
        assertThatCode(validator::validateSecrets).doesNotThrowAnyException();
    }

    @Test
    void validateSecrets_rejectsPublicAgentBindEvenWithVpnCidrs() {
        assertAgentBindRejectedInProd("203.0.113.1", "100.67.0.0/16", "BACKEND_AGENT_VPN_CIDRS");
    }

    @Test
    void validateSecrets_acceptsRfc1918AgentBindInProd() {
        SecuritySecretsValidator validator = prodValidator("192.168.1.10", null);
        assertThatCode(validator::validateSecrets).doesNotThrowAnyException();
    }

    @Test
    void validateSecrets_acceptsPrivateAgentBindInProd() {
        SecuritySecretsValidator validator = prodValidator("127.0.0.1", null);
        assertThatCode(validator::validateSecrets).doesNotThrowAnyException();
    }

    @Test
    void validateSecrets_skipsStrictChecksInDev() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});

        JwtConfig jwtConfig = mock(JwtConfig.class);
        AgentProperties agentProperties = mock(AgentProperties.class);
        when(jwtConfig.getSecret()).thenReturn("change-me-JWT_SECRET-at-least-32-chars");

        SecuritySecretsValidator validator = new SecuritySecretsValidator(
                environment, jwtConfig, agentProperties);
        ReflectionTestUtils.setField(validator, "backendAgentBind", "0.0.0.0");

        assertThatCode(validator::validateSecrets).doesNotThrowAnyException();
    }

    private void assertAgentBindRejectedInProd(String bind, String vpnCidrs, String messageFragment) {
        SecuritySecretsValidator validator = prodValidator(bind, vpnCidrs);

        assertThatThrownBy(validator::validateSecrets)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(messageFragment);
    }

    private static SecuritySecretsValidator prodValidator(String bind, String vpnCidrs) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});

        JwtConfig jwtConfig = mock(JwtConfig.class);
        AgentProperties agentProperties = mock(AgentProperties.class);
        when(jwtConfig.getSecret()).thenReturn("prod-jwt-secret-with-enough-length-32");
        when(agentProperties.getMasterSecret()).thenReturn("prod-agent-master-secret-32-chars-min");

        SecuritySecretsValidator validator = new SecuritySecretsValidator(
                environment, jwtConfig, agentProperties);
        ReflectionTestUtils.setField(validator, "credentialsEncryptionKey", "prod-credentials-key-32-chars-min");
        ReflectionTestUtils.setField(validator, "backendAgentBind", bind);
        ReflectionTestUtils.setField(validator, "backendAgentVpnCidrs", vpnCidrs != null ? vpnCidrs : "");
        return validator;
    }
}
