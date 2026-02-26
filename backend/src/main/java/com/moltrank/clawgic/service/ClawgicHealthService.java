package com.moltrank.clawgic.service;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import com.moltrank.clawgic.dto.ClawgicHealthResponse;
import com.moltrank.clawgic.model.ClawgicSkeletonStatus;
import org.springframework.stereotype.Service;

/**
 * Temporary Clawgic service stub to establish backend package boundaries.
 */
@Service
public class ClawgicHealthService {

    private final ClawgicRuntimeProperties clawgicRuntimeProperties;

    public ClawgicHealthService(ClawgicRuntimeProperties clawgicRuntimeProperties) {
        this.clawgicRuntimeProperties = clawgicRuntimeProperties;
    }

    public ClawgicHealthResponse health() {
        return new ClawgicHealthResponse(
                "clawgic",
                ClawgicSkeletonStatus.STUB,
                clawgicRuntimeProperties.isEnabled(),
                clawgicRuntimeProperties.isMockProvider(),
                clawgicRuntimeProperties.isMockJudge());
    }
}
