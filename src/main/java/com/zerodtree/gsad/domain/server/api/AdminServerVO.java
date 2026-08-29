package com.zerodtree.gsad.domain.server.api;

import java.time.Instant;

public record AdminServerVO(
        Long id,
        String serverId,
        String status,
        Instant lastReportedAt,
        String agentPsk
) {}
