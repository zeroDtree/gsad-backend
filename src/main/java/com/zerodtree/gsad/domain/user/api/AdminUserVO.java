package com.zerodtree.gsad.domain.user.api;

import java.time.Instant;

public record AdminUserVO(
        Long id,
        String email,
        String linuxUsername,
        String status,
        String cohort,
        String displayName,
        String studentId,
        String notes,
        String label,
        long activeAccessCount,
        Instant createdAt,
        Instant updatedAt
) {}
