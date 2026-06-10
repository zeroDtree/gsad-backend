package com.zerodtree.gsad.domain.server.api;

import java.time.Instant;
import java.util.List;

public record ServerVO(
        String id,
        String hostname,
        String resourceLevel,
        String status,
        Instant lastReportedAt,
        Instant collectedAt,
        GpuSummaryBlock summary,
        List<GpuRow> gpus
) {

    public record GpuSummaryBlock(
            Integer gpuCount,
            Double avgUtil,
            Integer avgMemUsedMb
    ) {}

    public record GpuRow(
            Integer index,
            String name,
            Double avgUtil,
            Integer memUsedMb,
            Integer memTotalMb
    ) {}
}
