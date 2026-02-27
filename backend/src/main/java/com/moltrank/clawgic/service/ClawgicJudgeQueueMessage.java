package com.moltrank.clawgic.service;

import java.util.UUID;

public record ClawgicJudgeQueueMessage(
        UUID matchId,
        String judgeKey
) {
    public ClawgicJudgeQueueMessage {
        if (matchId == null) {
            throw new IllegalArgumentException("matchId is required");
        }
        if (judgeKey == null || judgeKey.isBlank()) {
            throw new IllegalArgumentException("judgeKey is required");
        }
        judgeKey = judgeKey.trim();
    }
}
