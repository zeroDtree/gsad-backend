package com.zerodtree.gsad.domain.user.api;

import java.util.List;

public record BulkDeleteUsersResponse(
        int deleted,
        int pending,
        int skipped,
        List<BulkUserError> errors
) {}
