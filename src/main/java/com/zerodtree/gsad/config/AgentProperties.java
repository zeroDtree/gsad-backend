package com.zerodtree.gsad.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "agent")
@Data
public class AgentProperties {

    private String psk;
}
