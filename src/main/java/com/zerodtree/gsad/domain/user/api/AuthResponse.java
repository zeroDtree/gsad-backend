package com.zerodtree.gsad.domain.user.api;

import java.util.List;

public record AuthResponse(
        String token,
        String email,
        List<String> roles
) {}
