package com.zerodtree.gsad.domain.user.api;

import java.util.List;

public record BulkDisableUsersResponse(
        int disabled,
        int skipped,
        List<BulkUserError> errors
) {}
