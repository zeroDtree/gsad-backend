package com.zerodtree.gsad.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class SecuritySecretsValidator {

    private static final String DEFAULT_JWT_SECRET = "change-me-in-production-at-least-32-chars";
    private static final String DEFAULT_AGENT_MASTER_SECRET = "change-me-in-production";
    private static final String DEFAULT_ENCRYPTION_KEY = "change-me-32-chars-minimum";

    private final Environment environment;
    private final JwtConfig jwtConfig;
    private final AgentProperties agentProperties;

    @Value("${credentials.encryption-key:}")
    private String credentialsEncryptionKey;

    @Value("${BACKEND_AGENT_BIND:127.0.0.1}")
    private String backendAgentBind;

    @PostConstruct
    void validateSecrets() {
        if (isDevProfile()) {
            return;
        }

        assertSecret("JWT_SECRET", jwtConfig.getSecret(), DEFAULT_JWT_SECRET);
        assertSecret("AGENT_MASTER_SECRET", agentProperties.getMasterSecret(), DEFAULT_AGENT_MASTER_SECRET);
        assertSecret("CREDENTIALS_ENCRYPTION_KEY", credentialsEncryptionKey, DEFAULT_ENCRYPTION_KEY);

        if (isProdProfile() && "0.0.0.0".equals(backendAgentBind.trim())) {
            throw new IllegalStateException(
                    "Production forbids BACKEND_AGENT_BIND=0.0.0.0; bind to 127.0.0.1 or a private address");
        }
    }

    private void assertSecret(String envName, String value, String placeholder) {
        if (!StringUtils.hasText(value)
                || placeholder.equals(value)
                || value.length() < 32) {
            throw new IllegalStateException(
                    "Requires " + envName + " with at least 32 characters (not the default placeholder)");
        }
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    private boolean isProdProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
