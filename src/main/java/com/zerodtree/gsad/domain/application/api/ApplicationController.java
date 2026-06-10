package com.zerodtree.gsad.domain.application.api;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.common.PageResult;
import com.zerodtree.gsad.domain.application.service.ApplicationService;
import com.zerodtree.gsad.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "Applications")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    @Operation(summary = "Submit a new resource application")
    public ResponseEntity<ApiResponse<ApplicationVO>> create(
            @CurrentUserId Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateApplicationRequest request) {
        ApplicationVO vo = applicationService.create(userId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(vo));
    }

    @GetMapping("/mine")
    @Operation(summary = "List my applications (paginated)")
    public ApiResponse<PageResult<ApplicationVO>> listMine(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(applicationService.listMine(userId, status, page, pageSize));
    }
}
