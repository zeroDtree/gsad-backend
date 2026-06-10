package com.zerodtree.gsad.domain.server.api;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.domain.server.service.ServerService;
import com.zerodtree.gsad.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
@Tag(name = "Servers")
@SecurityRequirement(name = "bearerAuth")
public class ServerController {

    private final ServerService serverService;

    @GetMapping
    @Operation(summary = "List all servers with GPU summary")
    public ApiResponse<ServerListData> listServers(@CurrentUserId Long userId) {
        return ApiResponse.ok(new ServerListData(serverService.listServers()));
    }

    public record ServerListData(List<ServerVO> items) {}
}
