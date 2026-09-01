package com.zerodtree.gsad.security;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginRateLimitErrorData(int remainingAttempts, Integer retryAfterMinutes) {}
