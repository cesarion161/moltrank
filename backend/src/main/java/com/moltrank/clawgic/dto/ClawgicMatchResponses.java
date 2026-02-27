package com.moltrank.clawgic.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.moltrank.clawgic.model.ClawgicMatchStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ClawgicMatchResponses {

    private ClawgicMatchResponses() {
    }

    public record MatchSummary(
            UUID matchId,
            UUID tournamentId,
            UUID agent1Id,
            UUID agent2Id,
            Integer bracketRound,
            Integer bracketPosition,
            UUID nextMatchId,
            Integer nextMatchAgentSlot,
            ClawgicMatchStatus status,
            String phase,
            UUID winnerAgentId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record MatchDetail(
            UUID matchId,
            UUID tournamentId,
            UUID agent1Id,
            UUID agent2Id,
            Integer bracketRound,
            Integer bracketPosition,
            UUID nextMatchId,
            Integer nextMatchAgentSlot,
            ClawgicMatchStatus status,
            String phase,
            JsonNode transcriptJson,
            JsonNode judgeResultJson,
            UUID winnerAgentId,
            String forfeitReason,
            Integer judgeRetryCount,
            OffsetDateTime executionDeadlineAt,
            OffsetDateTime judgeDeadlineAt,
            OffsetDateTime startedAt,
            OffsetDateTime judgeRequestedAt,
            OffsetDateTime judgedAt,
            OffsetDateTime forfeitedAt,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
