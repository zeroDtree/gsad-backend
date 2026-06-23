package com.zerodtree.gsad.domain.user.api;

import com.zerodtree.gsad.common.ApiResponse;
import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.user.service.UserService;
import com.zerodtree.gsad.security.AuthCookieSupport;
import com.zerodtree.gsad.security.JwtAuthenticationToken;
import com.zerodtree.gsad.security.LoginRateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class UserController {

    private final UserService userService;
    private final AuthCookieSupport authCookieSupport;
    private final LoginRateLimitService loginRateLimitService;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @PostMapping("/login")
    @Operation(summary = "Login and obtain session cookie")
    public ResponseEntity<ApiResponse<SessionResponse>> login(
            HttpServletRequest request,
            @Valid @RequestBody LoginRequest body) {
        String clientIp = LoginRateLimitService.resolveClientIp(request);
        loginRateLimitService.assertAllowed(clientIp, body.email());
        loginRateLimitService.recordAttempt(clientIp, body.email());

        var result = userService.login(body);
        boolean secure = activeProfile.contains("prod");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieSupport.createTokenCookie(result.token(), secure).toString())
                .body(ApiResponse.ok(new SessionResponse(result.email(), result.roles())));
    }

    @GetMapping("/me")
    @Operation(summary = "Current session from JWT cookie")
    @SecurityRequirement(name = "sessionCookie")
    public ApiResponse<SessionResponse> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Not authenticated");
        }
        List<String> roles = jwtToken.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_") && !"ROLE_USER".equals(authority))
                .map(authority -> authority.substring("ROLE_".length()).toLowerCase())
                .toList();
        return ApiResponse.ok(new SessionResponse((String) jwtToken.getPrincipal(), roles));
    }

    @PostMapping("/logout")
    @Operation(summary = "Clear session cookie")
    public ResponseEntity<ApiResponse<Void>> logout() {
        boolean secure = activeProfile.contains("prod");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieSupport.clearTokenCookie(secure).toString())
                .body(ApiResponse.ok(null));
    }
}
