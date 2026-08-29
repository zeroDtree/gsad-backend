package com.zerodtree.gsad.security;

import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class AgentCredentialService {

    private final ServerRepository serverRepository;

    public boolean matches(String serverId, String presentedPsk) {
        if (!StringUtils.hasText(serverId) || !StringUtils.hasText(presentedPsk)) {
            return false;
        }
        return serverRepository.findByServerId(serverId.trim())
                .map(Server::getAgentPsk)
                .filter(StringUtils::hasText)
                .map(expected -> MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        presentedPsk.getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }
}
