package com.moltrank.config;

import com.moltrank.service.SolanaService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SolanaHealthIndicator implements HealthIndicator {

    private final SolanaService solanaService;

    public SolanaHealthIndicator(SolanaService solanaService) {
        this.solanaService = solanaService;
    }

    @Override
    public Health health() {
        try {
            String blockhash = solanaService.getLatestBlockhash();
            long slot = solanaService.getSlot();
            boolean programExists = solanaService.programExists();

            Health.Builder builder = programExists ? Health.up() : Health.down();
            return builder
                    .withDetail("latestBlockhash", blockhash)
                    .withDetail("slot", slot)
                    .withDetail("programId", solanaService.getProgramId())
                    .withDetail("programExists", programExists)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("programId", solanaService.getProgramId())
                    .withException(e)
                    .build();
        }
    }
}
