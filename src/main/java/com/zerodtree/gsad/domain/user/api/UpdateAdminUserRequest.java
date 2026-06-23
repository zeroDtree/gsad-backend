package com.zerodtree.gsad.domain.user.api;

import com.zerodtree.gsad.domain.user.model.UserStatus;

public record UpdateAdminUserRequest(
        String displayName,
        String cohort,
        String notes,
        String label,
        UserStatus status
) {}
