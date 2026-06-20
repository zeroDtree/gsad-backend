package com.zerodtree.gsad.domain.application.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.application.model.AuditStatus;
import com.zerodtree.gsad.domain.application.persistence.Application;
import com.zerodtree.gsad.domain.application.persistence.ApplicationRepository;
import com.zerodtree.gsad.domain.server.api.internal.PendingGrantTask;
import com.zerodtree.gsad.domain.server.api.internal.PendingRevokeTask;
import com.zerodtree.gsad.domain.server.api.internal.ProvisionCompleteRequest;
import com.zerodtree.gsad.domain.server.api.internal.RevokeCompleteRequest;
import com.zerodtree.gsad.domain.server.api.internal.ProvisionPendingResponse;
import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentProvisionService {

    private final ApplicationRepository applicationRepository;
    private final ServerRepository serverRepository;

    @Transactional(readOnly = true)
    public List<PendingGrantTask> findPendingGrants(String serverId) {
        return applicationRepository.findByAuditStatusAndServerId(AuditStatus.APPROVED, serverId).stream()
                .map(app -> new PendingGrantTask(
                        app.getId(),
                        app.getUserEmail(),
                        app.getServerId(),
                        app.getResourceLevel(),
                        app.getSshUsername(),
                        app.getSshPasswordPlain()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PendingRevokeTask> findPendingRevokes(String serverId) {
        return applicationRepository.findByAuditStatusAndServerId(AuditStatus.REVOKING, serverId).stream()
                .map(app -> new PendingRevokeTask(app.getId(), app.getSshUsername()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProvisionPendingResponse findPendingTasks(String serverId) {
        return new ProvisionPendingResponse(findPendingGrants(serverId), findPendingRevokes(serverId));
    }

    @Transactional
    public void completeProvision(ProvisionCompleteRequest request) {
        Application application = applicationRepository.findById(request.applicationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Application not found"));
        assertServerMatches(request.serverId(), application.getServerId());

        if (!Boolean.TRUE.equals(request.success())) {
            application.setAuditStatus(AuditStatus.FAILED_GRANT);
            application.setComment(StringUtils.hasText(request.errorMessage())
                    ? request.errorMessage()
                    : "Provision failed");
            applicationRepository.save(application);
            return;
        }

        if (!StringUtils.hasText(application.getSshPasswordPlain())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "No pending password for application");
        }

        String serverIp = resolveServerIp(request.serverIp(), application.getServerId());
        application.setServerIp(serverIp);
        application.setInitialPassword(application.getSshPasswordPlain());
        application.setSshPasswordPlain(null);
        application.setAuditStatus(AuditStatus.ACTIVE);
        application.setComment(null);
        applicationRepository.save(application);
    }

    @Transactional
    public void completeRevoke(RevokeCompleteRequest request) {
        Application application = applicationRepository.findById(request.applicationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Application not found"));
        assertServerMatches(request.serverId(), application.getServerId());

        if (!Boolean.TRUE.equals(request.success())) {
            application.setAuditStatus(AuditStatus.FAILED_REVOKE);
            application.setComment(StringUtils.hasText(request.errorMessage())
                    ? request.errorMessage()
                    : "Revoke failed");
            applicationRepository.save(application);
            return;
        }

        application.setInitialPassword(null);
        application.setAuditStatus(AuditStatus.REVOKED);
        application.setComment(null);
        applicationRepository.save(application);
    }

    private String resolveServerIp(String reportedIp, String serverId) {
        if (StringUtils.hasText(reportedIp)) {
            return reportedIp;
        }
        return serverRepository.findByServerId(serverId)
                .map(Server::getSshHost)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_ARGUMENT, "serverIp required when ssh_host is unset"));
    }

    private static void assertServerMatches(String serverId, String expectedServerId) {
        if (!expectedServerId.equals(serverId)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "serverId does not match application server");
        }
    }
}
