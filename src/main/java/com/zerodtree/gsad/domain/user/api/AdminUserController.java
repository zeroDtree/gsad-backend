package com.zerodtree.gsad.domain.user.api;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.common.PageResult;
import com.zerodtree.gsad.domain.user.service.AdminUserService;
import com.zerodtree.gsad.domain.user.service.UserImportService;
import com.zerodtree.gsad.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserImportService userImportService;
    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "List users (paginated, admin only)")
    public ApiResponse<PageResult<AdminUserVO>> listUsers(
            @RequestParam(required = false) String cohort,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(adminUserService.list(cohort, status, page, pageSize));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update user (admin only)")
    public ApiResponse<AdminUserVO> updateUser(
            @PathVariable Long id, @Valid @RequestBody UpdateAdminUserRequest request) {
        return ApiResponse.ok(adminUserService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user (admin only)")
    public ResponseEntity<?> deleteUser(
            @CurrentUserId Long adminUserId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean revokeSsh) {
        DeleteAdminUserResponse result = adminUserService.delete(adminUserId, id, revokeSsh);
        if (result.deleted()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>("STATE_CONFLICT", result.message(), result));
    }

    @PostMapping("/bulk-disable")
    @Operation(summary = "Bulk disable users (admin only)")
    public ApiResponse<BulkDisableUsersResponse> bulkDisableUsers(
            @CurrentUserId Long adminUserId,
            @Valid @RequestBody BulkUserActionRequest request) {
        return ApiResponse.ok(adminUserService.bulkDisable(adminUserId, request));
    }

    @PostMapping("/bulk-enable")
    @Operation(summary = "Bulk enable users (admin only)")
    public ApiResponse<BulkEnableUsersResponse> bulkEnableUsers(
            @CurrentUserId Long adminUserId,
            @Valid @RequestBody BulkUserActionRequest request) {
        return ApiResponse.ok(adminUserService.bulkEnable(adminUserId, request));
    }

    @PostMapping("/bulk-delete")
    @Operation(summary = "Bulk delete users (admin only)")
    public ApiResponse<BulkDeleteUsersResponse> bulkDeleteUsers(
            @CurrentUserId Long adminUserId,
            @Valid @RequestBody BulkDeleteUsersRequest request) {
        return ApiResponse.ok(adminUserService.bulkDelete(adminUserId, request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import users from CSV (admin only)")
    public ApiResponse<UserImportResponse> importUsers(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(userImportService.importCsv(file));
    }
}
