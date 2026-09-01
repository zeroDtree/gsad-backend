package com.zerodtree.gsad.domain.settings.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateLoginRateLimitSettingsRequest(
        @NotNull @Min(1) @Max(1440) Integer windowMinutes,
        @NotNull @Min(1) @Max(100) Integer maxAttemptsPerEmail,
        @NotNull @Min(1) @Max(1000) Integer maxAttemptsPerIp
) {}
