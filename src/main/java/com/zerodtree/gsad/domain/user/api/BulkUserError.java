package com.zerodtree.gsad.domain.user.api;

public record BulkUserError(
        Long userId,
        String email,
        String reason
) {}
