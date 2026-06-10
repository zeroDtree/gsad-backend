package com.zerodtree.gsad.domain.server.api.internal;

import java.util.List;

public record ServerReportResponse(
        List<PendingGrantTask> pendingGrants,
        List<PendingRevokeTask> pendingRevokes
) {
    public static ServerReportResponse empty() {
        return new ServerReportResponse(List.of(), List.of());
    }
}
