package com.zerodtree.gsad.domain.application.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ApplicationPasswordGenerator {

    private static final String ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int GENERATED_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public String resolvePassword(String requestedPassword) {
        if (requestedPassword != null && !requestedPassword.isBlank()) {
            return requestedPassword;
        }
        StringBuilder builder = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            builder.append(ALPHANUM.charAt(secureRandom.nextInt(ALPHANUM.length())));
        }
        return builder.toString();
    }
}
