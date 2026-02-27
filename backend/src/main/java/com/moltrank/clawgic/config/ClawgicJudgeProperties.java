package com.moltrank.clawgic.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Clawgic judge pipeline settings.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clawgic.judge")
public class ClawgicJudgeProperties {

    private boolean enabled = true;
    private String model = "gpt-4o";
    private boolean strictJson = true;
    private int maxRetries = 2;
    private int timeoutSeconds = 30;
    private List<String> keys = List.of("mock-judge-primary");
}
