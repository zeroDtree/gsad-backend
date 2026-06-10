package com.zerodtree.gsad.security;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class AuthorityUtils {

    private AuthorityUtils() {}

    public static List<String> parseRoles(String rolesCsv) {
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rolesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
