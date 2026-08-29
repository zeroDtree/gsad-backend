package com.zerodtree.gsad.domain.server.api;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.common.PageResult;
import com.zerodtree.gsad.domain.server.service.AdminServerService;
import com.zerodtree.gsad.domain.server.service.ServerImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AdminServerService adminServerService;

    @GetMapping
    @Operation(summary = "List GPU servers (paginated, admin only)")
    public ApiResponse<PageResult<AdminServerVO>> listServers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(adminServerService.list(page, pageSize));
    }

    @PostMapping
    @Operation(summary = "Create a GPU server (admin only)")
    public ApiResponse<AdminServerVO> createServer(@Valid @RequestBody CreateAdminServerRequest request) {
        return ApiResponse.ok(adminServerService.create(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a GPU server (admin only)")
    public ApiResponse<AdminServerVO> updateServer(
            @PathVariable Long id, @Valid @RequestBody UpdateAdminServerRequest request) {
        return ApiResponse.ok(adminServerService.update(id, request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import GPU servers from CSV (admin only)")
    public ApiResponse<ServerImportResponse> importServers(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(serverImportService.importCsv(file));
    }
}
