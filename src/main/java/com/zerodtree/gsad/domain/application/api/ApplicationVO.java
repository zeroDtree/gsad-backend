package com.zerodtree.gsad.domain.application.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

public record ApplicationVO(
        String id,
        String serverId,
        String resourceLevel,
        String auditStatus,
        String comment,
        String serverIp,
        String sshUsername,
        @JsonInclude(JsonInclude.Include.NON_NULL) String initialPassword,
        boolean credentialsReady,
        boolean installMiniconda,
        Instant createdAt,
        Instant updatedAt
) {}
