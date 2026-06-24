package com.zerodtree.gsad.security;

import java.util.List;

public record JwtUserClaims(String email, Long userId, List<String> roles, String passwordFingerprint) {}
