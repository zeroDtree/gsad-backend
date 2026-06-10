package com.zerodtree.gsad.domain.application.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateApplicationRequest(
        @NotBlank String serverId,
        @NotBlank @Size(max = 500) String purpose,
        @NotNull @Min(1) Integer requestedDays,
        @NotNull Instant requestedStartAt,
        @Size(min = 8, max = 128) String sshPassword
) {}
