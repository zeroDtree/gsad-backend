package com.zerodtree.gsad.domain.user.api;

import java.util.List;

public record SessionResponse(
        String email,
        List<String> roles
) {}
