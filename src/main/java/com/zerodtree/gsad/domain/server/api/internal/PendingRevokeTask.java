package com.zerodtree.gsad.domain.server.api.internal;

public record PendingRevokeTask(
        String applicationId,
        String linuxUsername
) {}
