package com.zerodtree.gsad.domain.application.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(
        @NotBlank String serverId,
        @Size(min = 8, max = 128) String sshPassword,
        Boolean installMiniconda
) {}
