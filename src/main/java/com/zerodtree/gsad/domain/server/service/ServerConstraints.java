package com.zerodtree.gsad.domain.server.service;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class ServerConstraints {

    public static final int MIN_AGENT_PSK_LENGTH = 16;

    private static final Pattern SERVER_ID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,254}$");

    private ServerConstraints() {}

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public static String serverIdError(String serverId) {
        if (!StringUtils.hasText(serverId)) {
            return "server_id is required";
        }
        if (serverId.length() > 255) {
            return "server_id must be at most 255 characters";
        }
        if (!SERVER_ID_PATTERN.matcher(serverId).matches()) {
            return "server_id must start with alphanumeric and contain only letters, digits, ., _, -";
        }
        return null;
    }

    public static String agentPskError(String agentPsk) {
        if (!StringUtils.hasText(agentPsk)) {
            return "agent_psk is required";
        }
        if (agentPsk.length() < MIN_AGENT_PSK_LENGTH) {
            return "agent_psk must be at least " + MIN_AGENT_PSK_LENGTH + " characters";
        }
        return null;
    }
}
