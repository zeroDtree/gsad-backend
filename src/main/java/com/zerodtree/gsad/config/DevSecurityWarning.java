package com.zerodtree.gsad.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Slf4j
public class DevSecurityWarning {

    @PostConstruct
    void warnDefaultCredentials() {
        log.warn(
                "DEV MODE: default admin is admin@gsad.local / Admin@123456 — do not expose this stack to the internet");
    }
}
