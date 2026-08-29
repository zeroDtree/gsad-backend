package com.zerodtree.gsad.domain.server.api;

public record UpdateAdminServerRequest(
        String serverId,
        String agentPsk
) {}
