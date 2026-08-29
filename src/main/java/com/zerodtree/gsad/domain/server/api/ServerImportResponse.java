package com.zerodtree.gsad.domain.server.api;

import java.util.List;

public record ServerImportResponse(
        int created,
        int updated,
        List<ServerImportError> errors
) {}
