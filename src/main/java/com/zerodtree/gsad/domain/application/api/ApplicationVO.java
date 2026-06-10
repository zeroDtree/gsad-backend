package com.zerodtree.gsad.domain.application.api;

import java.time.Instant;

public record ApplicationVO(
        String id,
        String serverId,
        String resourceLevel,
        String purpose,
        Integer requestedDays,
        Instant requestedStartAt,
        Instant expireAt,
        String auditStatus,
        String comment,
        String serverIp,
        String sshUsername,
        boolean credentialsReady,
        boolean passwordDelivered,
        Instant createdAt,
        Instant updatedAt
) {}
