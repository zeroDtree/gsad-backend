package com.zerodtree.gsad.domain.server.api.internal;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.domain.application.service.AgentProvisionService;
import com.zerodtree.gsad.domain.server.api.ServerReportRequest;
import com.zerodtree.gsad.domain.server.service.ServerService;
import io.swagger.v3.oas.annotations.Hidden;
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

    @PostMapping("/report")
    public ApiResponse<Void> report(@Valid @RequestBody ServerReportRequest request) {
        serverService.reportMetrics(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/provision/pending")
    public ApiResponse<ProvisionPendingResponse> provisionPending(
            @Valid @RequestBody ProvisionPendingRequest request) {
        return ApiResponse.ok(agentProvisionService.findPendingTasks(request.serverId()));
    }

    @PostMapping("/provision/complete")
    public ApiResponse<Void> provisionComplete(@Valid @RequestBody ProvisionCompleteRequest request) {
        agentProvisionService.completeProvision(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/revoke/complete")
    public ApiResponse<Void> revokeComplete(@Valid @RequestBody RevokeCompleteRequest request) {
        agentProvisionService.completeRevoke(request);
        return ApiResponse.ok(null);
    }
}
