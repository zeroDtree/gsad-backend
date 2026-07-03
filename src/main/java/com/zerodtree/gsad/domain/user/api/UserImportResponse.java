package com.zerodtree.gsad.domain.user.api;

import java.util.List;

public record UserImportResponse(
        int created,
        int updated,
        List<UserImportError> errors
) {}
