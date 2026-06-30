package com.zerodtree.gsad.domain.server.api.internal;

public record PendingGrantTask(
        String applicationId,
        String email,
        String serverId,
        String resourceLevel,
        String linuxUsername,
        String password,
        boolean installMiniconda
) {}
