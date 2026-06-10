package com.zerodtree.gsad.domain.application.api;

import java.time.Instant;

/**
 * Create response; SSH credentials appear after external provision completes (ACTIVE).
 */
public record ApplicationCreateVO(
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
        String initialPassword,
        boolean credentialsReady,
        boolean passwordDelivered,
        Instant createdAt,
        Instant updatedAt
) {}
