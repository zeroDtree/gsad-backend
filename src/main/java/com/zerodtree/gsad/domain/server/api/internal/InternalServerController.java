package com.zerodtree.gsad.domain.server.api.internal;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.domain.application.service.AgentProvisionService;
import com.zerodtree.gsad.domain.server.api.ServerReportRequest;
import com.zerodtree.gsad.domain.server.service.ServerService;
import com.zerodtree.gsad.security.AgentServerGuard;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/servers")
@RequiredArgsConstructor
@Hidden
public class InternalServerController {

    private final ServerService serverService;
    private final AgentProvisionService agentProvisionService;
    private final AgentServerGuard agentServerGuard;

    @PostMapping("/report")
    public ApiResponse<Void> report(
            HttpServletRequest httpRequest,
            @Valid @RequestBody ServerReportRequest request) {
        agentServerGuard.assertBodyServerId(httpRequest, request.serverId());
        serverService.reportMetrics(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/provision/pending")
    public ApiResponse<ProvisionPendingResponse> provisionPending(
            HttpServletRequest httpRequest,
            @Valid @RequestBody ProvisionPendingRequest request) {
        agentServerGuard.assertBodyServerId(httpRequest, request.serverId());
        return ApiResponse.ok(agentProvisionService.findPendingTasks(request.serverId()));
    }

    @PostMapping("/provision/complete")
    public ApiResponse<Void> provisionComplete(
            HttpServletRequest httpRequest,
            @Valid @RequestBody ProvisionCompleteRequest request) {
        agentServerGuard.assertBodyServerId(httpRequest, request.serverId());
        agentProvisionService.completeProvision(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/revoke/complete")
    public ApiResponse<Void> revokeComplete(
            HttpServletRequest httpRequest,
            @Valid @RequestBody RevokeCompleteRequest request) {
        agentServerGuard.assertBodyServerId(httpRequest, request.serverId());
        agentProvisionService.completeRevoke(request);
        return ApiResponse.ok(null);
    }
}
