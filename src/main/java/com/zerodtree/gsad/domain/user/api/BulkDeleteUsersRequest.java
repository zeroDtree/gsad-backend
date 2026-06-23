package com.zerodtree.gsad.domain.user.api;

import java.util.List;

public record BulkDeleteUsersRequest(
        List<Long> ids,
        Boolean selectAll,
        String cohort,
        String status,
        boolean revokeSsh,
        String role
) {}
