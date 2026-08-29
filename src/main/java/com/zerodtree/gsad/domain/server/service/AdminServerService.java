package com.zerodtree.gsad.domain.server.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.common.PageResult;
import com.zerodtree.gsad.domain.application.persistence.ApplicationRepository;
import com.zerodtree.gsad.domain.server.api.AdminServerVO;
import com.zerodtree.gsad.domain.server.api.CreateAdminServerRequest;
import com.zerodtree.gsad.domain.server.api.UpdateAdminServerRequest;
import com.zerodtree.gsad.domain.server.model.ServerStatus;
import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminServerService {

    private final ServerRepository serverRepository;
    private final ApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public PageResult<AdminServerVO> list(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);
        Page<Server> resultPage = serverRepository.findAllByOrderByServerIdAsc(pageable);
        return PageResult.of(
                resultPage.getContent().stream().map(this::toVo).toList(),
                resultPage.getTotalElements(),
                safePage,
                safeSize);
    }

    @Transactional
    public AdminServerVO create(CreateAdminServerRequest request) {
        String serverId = requireValidServerId(request.serverId());
        String agentPsk = requireValidAgentPsk(request.agentPsk());
        if (serverRepository.existsByServerId(serverId)) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "server_id already exists");
        }

        Server server = new Server();
        server.setServerId(serverId);
        server.setAgentPsk(agentPsk);
        server.setStatus(ServerStatus.OFFLINE);
        server.setMetricsJson("{}");
        return toVo(serverRepository.save(server));
    }

    @Transactional
    public AdminServerVO update(Long id, UpdateAdminServerRequest request) {
        Server server = serverRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Server not found"));

        if (request.serverId() != null) {
            String serverId = requireValidServerId(request.serverId());
            if (!serverId.equals(server.getServerId())) {
                if (serverRepository.existsByServerIdAndIdNot(serverId, id)) {
                    throw new BusinessException(ErrorCode.STATE_CONFLICT, "server_id already exists");
                }
                String previousId = server.getServerId();
                server.setServerId(serverId);
                applicationRepository.updateServerId(previousId, serverId);
            }
        }

        if (request.agentPsk() != null) {
            server.setAgentPsk(requireValidAgentPsk(request.agentPsk()));
        }

        return toVo(serverRepository.save(server));
    }

    private AdminServerVO toVo(Server server) {
        return new AdminServerVO(
                server.getId(),
                server.getServerId(),
                server.getStatus() != null ? server.getStatus().name() : null,
                server.getLastReportedAt(),
                server.getAgentPsk());
    }

    private static String requireValidServerId(String raw) {
        String serverId = ServerConstraints.normalize(raw);
        String error = ServerConstraints.serverIdError(serverId);
        if (error != null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, error);
        }
        return serverId;
    }

    private static String requireValidAgentPsk(String raw) {
        String agentPsk = ServerConstraints.normalize(raw);
        String error = ServerConstraints.agentPskError(agentPsk);
        if (error != null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, error);
        }
        return agentPsk;
    }
}
