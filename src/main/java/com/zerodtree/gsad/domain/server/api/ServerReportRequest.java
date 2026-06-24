package com.zerodtree.gsad.domain.server.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record ServerReportRequest(
        @NotBlank String serverId,
        String resourceLevel,
        Instant collectedAt,
        @NotNull @Valid GpuSummaryBlock summary,
        @NotNull List<@Valid GpuRow> gpus
) {

    public record GpuSummaryBlock(
            @NotNull Integer gpuCount,
            @NotNull Double avgUtil,
            @NotNull Integer avgMemUsedMb
    ) {}

    public record GpuRow(
            @NotNull Integer index,
            @NotBlank String name,
            @NotNull Double avgUtil,
            @NotNull Integer memUsedMb,
            @NotNull Integer memTotalMb
    ) {}
}
