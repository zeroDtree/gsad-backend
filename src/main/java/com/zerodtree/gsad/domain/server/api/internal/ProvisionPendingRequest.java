package com.zerodtree.gsad.domain.server.api.internal;

import jakarta.validation.constraints.NotBlank;

public record ProvisionPendingRequest(
        @NotBlank String serverId
) {}
