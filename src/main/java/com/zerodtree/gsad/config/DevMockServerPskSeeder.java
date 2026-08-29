package com.zerodtree.gsad.config;

import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevMockServerPskSeeder implements ApplicationRunner {

    private static final String MOCK_SERVER_PREFIX = "gpu-mock-";

    private final ServerRepository serverRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = 0;
        for (Server server : serverRepository.findAllByOrderByServerIdAsc()) {
            if (!server.getServerId().startsWith(MOCK_SERVER_PREFIX)) {
                continue;
            }
            if (StringUtils.hasText(server.getAgentPsk())) {
                continue;
            }
            server.setAgentPsk(DevMockDefaults.AGENT_PSK);
            serverRepository.save(server);
            updated++;
        }
        if (updated > 0) {
            log.info("Seeded agent PSK on {} mock server(s)", updated);
        }
    }
}
