package com.zerodtree.gsad.domain.settings.api;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.domain.settings.service.LoginRateLimitSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@Tag(name = "Admin")
@SecurityRequirement(name = "sessionCookie")
public class AdminSettingsController {

    private final LoginRateLimitSettingsService settingsService;

    @GetMapping
    @Operation(summary = "Get login rate-limit settings (admin only)")
    public ApiResponse<LoginRateLimitSettingsResponse> get() {
        return ApiResponse.ok(settingsService.get());
    }

    @PatchMapping
    @Operation(summary = "Update login rate-limit settings (admin only)")
    public ApiResponse<LoginRateLimitSettingsResponse> update(
            @Valid @RequestBody UpdateLoginRateLimitSettingsRequest request) {
        return ApiResponse.ok(settingsService.update(request));
    }
}
