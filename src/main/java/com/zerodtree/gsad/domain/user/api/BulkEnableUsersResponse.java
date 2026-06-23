package com.zerodtree.gsad.domain.user.api;

import java.util.List;

public record BulkEnableUsersResponse(
        int enabled,
        int skipped,
        List<BulkUserError> errors
) {}
