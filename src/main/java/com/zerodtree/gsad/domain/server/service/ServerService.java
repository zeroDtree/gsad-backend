package com.zerodtree.gsad.domain.server.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.server.api.ServerReportRequest;
import com.zerodtree.gsad.domain.server.api.ServerVO;
import com.zerodtree.gsad.domain.server.model.ServerStatus;
import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import com.zerodtree.gsad.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository serverRepository;
    private final ServerMetricsReader metricsReader;
    private final AgentProperties agentProperties;

    @Transactional(readOnly = true)
    public List<ServerVO> listServers() {
        return serverRepository.findAllByOrderByServerIdAsc().stream()
                .map(server -> ServerMapper.toVo(server, metricsReader.parse(server.getMetricsJson())))
                .toList();
    }

    @Transactional
    public void reportMetrics(ServerReportRequest request) {
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

        Server server = serverRepository.findByServerId(request.serverId())
                .orElseGet(() -> {
                    if (!agentProperties.isAllowServerRegistration()) {
                        throw new BusinessException(ErrorCode.NOT_FOUND, "Server not found");
                    }
                    Server created = new Server();
                    created.setServerId(request.serverId());
                    created.setSshHost(null);
                    return created;
                });

        if (StringUtils.hasText(request.resourceLevel())) {
            server.setResourceLevel(request.resourceLevel().trim());
        }
        server.setStatus(ServerStatus.ONLINE);
        server.setLastReportedAt(Instant.now());
        server.setMetricsJson(metricsJson);
        serverRepository.save(server);
    }

    @Transactional(readOnly = true)
    public Server requireByServerId(String serverId) {
        return serverRepository.findByServerId(serverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Server not found"));
    }
}
