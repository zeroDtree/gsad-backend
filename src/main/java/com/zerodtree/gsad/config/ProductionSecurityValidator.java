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
    private static final String DEFAULT_AGENT_PSK = "change-me-in-production";
    private static final String DEFAULT_ENCRYPTION_KEY = "change-me-32-chars-minimum";

    private final JwtConfig jwtConfig;
    private final AgentProperties agentProperties;

    @Value("${credentials.encryption-key:}")
    private String credentialsEncryptionKey;

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

        String psk = agentProperties.getPsk();
        if (psk == null || psk.isBlank() || DEFAULT_AGENT_PSK.equals(psk)) {
            throw new IllegalStateException(
                    "Production requires AGENT_PSK env var (not the default value)");
        }

        if (!StringUtils.hasText(credentialsEncryptionKey)
                || DEFAULT_ENCRYPTION_KEY.equals(credentialsEncryptionKey)
                || credentialsEncryptionKey.length() < 32) {
            throw new IllegalStateException(
                    "Production requires CREDENTIALS_ENCRYPTION_KEY with at least 32 characters");
        }
    }
}
