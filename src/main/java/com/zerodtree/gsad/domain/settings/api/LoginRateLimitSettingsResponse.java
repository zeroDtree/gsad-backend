package com.zerodtree.gsad.domain.settings.api;

public record LoginRateLimitSettingsResponse(
        int windowMinutes,
        int maxAttemptsPerEmail,
        int maxAttemptsPerIp
) {}
