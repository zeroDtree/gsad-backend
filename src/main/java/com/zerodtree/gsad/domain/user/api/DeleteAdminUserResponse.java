package com.zerodtree.gsad.domain.user.api;

public record DeleteAdminUserResponse(
        boolean deleted,
        int pendingRevokes,
        String message
) {}
