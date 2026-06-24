package com.zerodtree.gsad.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class ProductionSecurityValidator {

    private static final String DEFAULT_JWT_SECRET = "change-me-in-production-at-least-32-chars";
    private static final String DEFAULT_AGENT_MASTER_SECRET = "change-me-in-production";
    private static final String DEFAULT_ENCRYPTION_KEY = "change-me-32-chars-minimum";

    private final JwtConfig jwtConfig;
    private final AgentProperties agentProperties;

    @Value("${credentials.encryption-key:}")
    private String credentialsEncryptionKey;

    @Value("${BACKEND_AGENT_BIND:127.0.0.1}")
    private String backendAgentBind;

    @PostConstruct
    void validateSecrets() {
        String jwtSecret = jwtConfig.getSecret();
        if (jwtSecret == null
                || jwtSecret.isBlank()
                || DEFAULT_JWT_SECRET.equals(jwtSecret)
                || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "Production requires JWT_SECRET env var with at least 32 characters (not the default value)");
        }

        String masterSecret = agentProperties.getMasterSecret();
        if (masterSecret == null
                || masterSecret.isBlank()
                || DEFAULT_AGENT_MASTER_SECRET.equals(masterSecret)
                || masterSecret.length() < 32) {
            throw new IllegalStateException(
                    "Production requires AGENT_MASTER_SECRET env var with at least 32 characters (not the default value)");
        }

        if (!StringUtils.hasText(credentialsEncryptionKey)
                || DEFAULT_ENCRYPTION_KEY.equals(credentialsEncryptionKey)
                || credentialsEncryptionKey.length() < 32) {
            throw new IllegalStateException(
                    "Production requires CREDENTIALS_ENCRYPTION_KEY with at least 32 characters");
        }

        if ("0.0.0.0".equals(backendAgentBind.trim())) {
            throw new IllegalStateException(
                    "Production forbids BACKEND_AGENT_BIND=0.0.0.0; bind to 127.0.0.1 or a private address");
        }
    }
}
