package com.moltrank.clawgic.dto;

import com.moltrank.clawgic.model.ClawgicSkeletonStatus;

/**
 * Minimal response DTO used while the Clawgic API surface is being built out.
 */
public record ClawgicHealthResponse(
        String service,
        ClawgicSkeletonStatus status,
        boolean clawgicEnabled,
        boolean mockProvider,
        boolean mockJudge) {
}
