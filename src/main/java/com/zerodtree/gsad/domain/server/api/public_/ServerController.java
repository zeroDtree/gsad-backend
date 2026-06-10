package com.zerodtree.gsad.domain.server.api.public_;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.domain.server.api.ServerVO;
import com.zerodtree.gsad.domain.server.service.ServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/servers")
@RequiredArgsConstructor
@Tag(name = "Servers")
public class ServerController {

    private final ServerService serverService;

    @GetMapping
    @Operation(summary = "List all servers with GPU summary")
    public ApiResponse<ServerListData> listServers() {
        return ApiResponse.ok(new ServerListData(serverService.listPublicServers()));
    }

    public record ServerListData(List<ServerVO> items) {}
}
