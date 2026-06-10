package com.zerodtree.gsad.domain.server.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerodtree.gsad.domain.server.api.ServerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ServerMetricsReader {

    private final ObjectMapper objectMapper;

    public MetricsSnapshot parse(String metricsJson) {
        if (metricsJson == null || metricsJson.isBlank()) {
            return MetricsSnapshot.empty();
        }
        try {
            MetricsDocument doc = objectMapper.readValue(metricsJson, MetricsDocument.class);
            ServerVO.GpuSummaryBlock summary = null;
            if (doc.summary() != null) {
                summary = new ServerVO.GpuSummaryBlock(
                        doc.summary().gpuCount(),
                        doc.summary().avgUtil(),
                        doc.summary().avgMemUsedMb());
            }
            List<ServerVO.GpuRow> gpus = doc.gpus() == null ? List.of() : doc.gpus().stream()
                    .map(g -> new ServerVO.GpuRow(
                            g.index(), g.name(), g.avgUtil(), g.memUsedMb(), g.memTotalMb()))
                    .toList();
            Instant collectedAt = doc.collectedAt() != null
                    ? Instant.parse(doc.collectedAt())
                    : null;
            return new MetricsSnapshot(collectedAt, summary, gpus);
        } catch (Exception e) {
            return MetricsSnapshot.empty();
        }
    }

    public String toJson(Instant collectedAt,
            ServerReportRequestSummary summary,
            List<ServerReportGpuRow> gpus) {
        try {
            MetricsDocument doc = new MetricsDocument(
                    collectedAt != null ? collectedAt.toString() : null,
                    summary != null ? new MetricsSummary(
                            summary.gpuCount(), summary.avgUtil(), summary.avgMemUsedMb()) : null,
                    gpus == null ? List.of() : gpus.stream()
                            .map(g -> new MetricsGpu(
                                    g.index(), g.name(), g.avgUtil(), g.memUsedMb(), g.memTotalMb()))
                            .toList());
            return objectMapper.writeValueAsString(doc);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize metrics JSON", e);
        }
    }

    public record MetricsSnapshot(
            Instant collectedAt,
            ServerVO.GpuSummaryBlock summary,
            List<ServerVO.GpuRow> gpus
    ) {
        static MetricsSnapshot empty() {
            return new MetricsSnapshot(null, null, Collections.emptyList());
        }
    }

    public record ServerReportRequestSummary(Integer gpuCount, Double avgUtil, Integer avgMemUsedMb) {}

    public record ServerReportGpuRow(
            Integer index, String name, Double avgUtil, Integer memUsedMb, Integer memTotalMb) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetricsDocument(
            String collectedAt,
            MetricsSummary summary,
            List<MetricsGpu> gpus
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetricsSummary(Integer gpuCount, Double avgUtil, Integer avgMemUsedMb) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetricsGpu(
            Integer index, String name, Double avgUtil, Integer memUsedMb, Integer memTotalMb) {}
}
