package com.moltrank.clawgic.provider;

import com.moltrank.clawgic.model.DebateTranscriptMessage;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fully-resolved request payload for a single judge attempt.
 */
public record ClawgicJudgeRequest(
        UUID matchId,
        UUID tournamentId,
        UUID agent1Id,
        UUID agent2Id,
        String topic,
        List<DebateTranscriptMessage> transcript,
        String judgeKey,
        String model
) {
    public ClawgicJudgeRequest {
        Objects.requireNonNull(matchId, "matchId is required");
        Objects.requireNonNull(tournamentId, "tournamentId is required");
        Objects.requireNonNull(agent1Id, "agent1Id is required");
        Objects.requireNonNull(agent2Id, "agent2Id is required");

        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("topic is required");
        }
        topic = topic.trim();

        transcript = transcript == null ? List.of() : List.copyOf(transcript);

        if (!StringUtils.hasText(judgeKey)) {
            throw new IllegalArgumentException("judgeKey is required");
        }
        judgeKey = judgeKey.trim();

        if (!StringUtils.hasText(model)) {
            throw new IllegalArgumentException("model is required");
        }
        model = model.trim();
    }
}
