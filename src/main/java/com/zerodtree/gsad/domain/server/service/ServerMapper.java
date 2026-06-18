package com.zerodtree.gsad.domain.server.service;

import com.zerodtree.gsad.domain.server.api.ServerVO;
import com.zerodtree.gsad.domain.server.persistence.Server;

public final class ServerMapper {

    private ServerMapper() {}

    public static ServerVO toVo(Server server, ServerMetricsReader.MetricsSnapshot metrics) {
        return new ServerVO(
                server.getServerId(),
                server.getResourceLevel(),
                server.getStatus().name(),
                server.getLastReportedAt(),
                metrics.collectedAt(),
                metrics.summary(),
                metrics.gpus());
    }
}
