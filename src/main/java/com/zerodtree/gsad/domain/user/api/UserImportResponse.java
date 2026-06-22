package com.zerodtree.gsad.domain.user.api;

import java.util.List;

public record UserImportResponse(
        int created,
        int skipped,
        List<UserImportError> errors,
        List<UserImportPassword> passwords
) {}
