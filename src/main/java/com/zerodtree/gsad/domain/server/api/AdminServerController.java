package com.zerodtree.gsad.domain.server.api;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.domain.server.service.ServerImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/servers")
@RequiredArgsConstructor
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminServerController {

    private final ServerImportService serverImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import GPU servers from CSV (admin only)")
    public ApiResponse<ServerImportResponse> importServers(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(serverImportService.importCsv(file));
    }
}
