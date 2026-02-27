package com.moltrank.clawgic.service;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClawgicMatchLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClawgicMatchLifecycleScheduler.class);

    private final ClawgicRuntimeProperties clawgicRuntimeProperties;
    private final ClawgicMatchLifecycleService clawgicMatchLifecycleService;

    @Scheduled(
            fixedRateString = "${clawgic.worker.poll-interval-ms:10000}",
            initialDelayString = "${clawgic.worker.initial-delay-ms:5000}"
    )
    public void processLifecycleTick() {
        if (!clawgicRuntimeProperties.isEnabled() || !clawgicRuntimeProperties.getWorker().isEnabled()) {
            return;
        }

        ClawgicMatchLifecycleService.TickSummary tickSummary = clawgicMatchLifecycleService.processLifecycleTick();
        if (tickSummary.hasWork()) {
            log.info(
                    "Clawgic worker tick: tournamentsActivated={}, winnersPropagated={}, matchesExecuted={}",
                    tickSummary.tournamentsActivated(),
                    tickSummary.winnersPropagated(),
                    tickSummary.matchesExecuted()
            );
        } else {
            log.debug("Clawgic worker tick completed with no state changes");
        }
    }
}
