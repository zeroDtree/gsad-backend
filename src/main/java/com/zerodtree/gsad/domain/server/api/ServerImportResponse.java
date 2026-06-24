package com.zerodtree.gsad.domain.server.api;

import java.util.List;

public record ServerImportResponse(
        int created,
        int skipped,
        List<ServerImportError> errors
) {}
