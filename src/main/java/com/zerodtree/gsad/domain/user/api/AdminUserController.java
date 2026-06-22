package com.zerodtree.gsad.domain.user.api;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.domain.user.service.UserImportService;
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
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserImportService userImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import users from CSV (admin only)")
    public ApiResponse<UserImportResponse> importUsers(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(userImportService.importCsv(file));
    }
}
