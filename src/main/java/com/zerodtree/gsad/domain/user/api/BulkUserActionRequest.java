package com.zerodtree.gsad.domain.user.api;

import java.util.List;

public record BulkUserActionRequest(
        List<Long> ids,
        Boolean selectAll,
        String cohort,
        String status
) {}
