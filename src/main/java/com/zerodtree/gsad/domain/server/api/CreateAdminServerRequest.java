package com.zerodtree.gsad.domain.server.api;

import jakarta.validation.constraints.NotBlank;

public record CreateAdminServerRequest(
        @NotBlank String serverId,
        @NotBlank String agentPsk
) {}
