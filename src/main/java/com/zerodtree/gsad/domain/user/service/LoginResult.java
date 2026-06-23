package com.zerodtree.gsad.domain.user.service;

import java.util.List;

public record LoginResult(String token, String email, List<String> roles) {}
