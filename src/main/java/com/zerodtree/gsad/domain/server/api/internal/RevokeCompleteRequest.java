package com.zerodtree.gsad.domain.server.api.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RevokeCompleteRequest(
        @NotBlank String applicationId,
        @NotBlank String hostname,
        @NotNull Boolean success,
        String errorMessage
) {}
