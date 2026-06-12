package com.bft.drone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bully")
public class BullyConfig {
    private long heartbeatIntervalMs = 3000;
    private long heartbeatTimeoutMs  = 15000;
    private long electionTimeoutMs   = 4000;
}
