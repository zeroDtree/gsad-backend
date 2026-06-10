package com.zerodtree.gsad.domain.server.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.application.service.AgentProvisionService;
import com.zerodtree.gsad.domain.server.api.ServerReportRequest;
import com.zerodtree.gsad.domain.server.api.ServerVO;
import com.zerodtree.gsad.domain.server.api.internal.ServerReportResponse;
import com.zerodtree.gsad.domain.server.model.ServerStatus;
import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository serverRepository;
    private final ServerMetricsReader metricsReader;
    private final AgentProvisionService agentProvisionService;

    @Transactional(readOnly = true)
    public List<ServerVO> listPublicServers() {
        return serverRepository.findAllByOrderByServerIdAsc().stream()
                .map(server -> ServerMapper.toVo(server, metricsReader.parse(server.getMetricsJson())))
                .toList();
    }

    @Transactional
    public ServerReportResponse reportMetrics(ServerReportRequest request) {
        Instant collectedAt = request.collectedAt() != null ? request.collectedAt() : Instant.now();
        String metricsJson = metricsReader.toJson(
                collectedAt,
                new ServerMetricsReader.ServerReportRequestSummary(
                        request.summary().gpuCount(),
                        request.summary().avgUtil(),
                        request.summary().avgMemUsedMb()),
                request.gpus().stream()
                        .map(g -> new ServerMetricsReader.ServerReportGpuRow(
                                g.index(), g.name(), g.avgUtil(), g.memUsedMb(), g.memTotalMb()))
                        .toList());

        Server server = serverRepository.findByHostname(request.hostname())
                .orElseGet(() -> {
                    Server created = new Server();
                    created.setHostname(request.hostname());
                    created.setServerId(deriveServerId(request.hostname()));
                    created.setSshHost(null);
                    return created;
                });

        server.setResourceLevel(request.resourceLevel());
        server.setStatus(ServerStatus.ONLINE);
        server.setLastReportedAt(Instant.now());
        server.setAvgUtil(BigDecimal.valueOf(request.summary().avgUtil()).setScale(4, RoundingMode.HALF_UP));
        server.setAvgMemUsedMb(request.summary().avgMemUsedMb());
        server.setMetricsJson(metricsJson);
        serverRepository.save(server);

        return new ServerReportResponse(
                agentProvisionService.findPendingGrants(server.getServerId()),
                agentProvisionService.findPendingRevokes(server.getServerId()));
    }

    @Transactional(readOnly = true)
    public Server requireByServerId(String serverId) {
        return serverRepository.findByServerId(serverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Server not found"));
    }

    static String deriveServerId(String hostname) {
        return AgentProvisionService.deriveServerId(hostname);
    }
}
