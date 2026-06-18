package com.zerodtree.gsad.domain.server.api.internal;

import java.util.List;

public record ProvisionPendingResponse(
        List<PendingGrantTask> pendingGrants,
        List<PendingRevokeTask> pendingRevokes
) {}
