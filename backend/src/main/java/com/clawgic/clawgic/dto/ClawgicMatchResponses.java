package com.clawgic.clawgic.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.clawgic.clawgic.model.DebatePhase;
import com.clawgic.clawgic.model.ClawgicMatchJudgementStatus;
import com.clawgic.clawgic.model.ClawgicMatchStatus;

import java.time.OffsetDateTime;
import java.util.List;
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
            DebatePhase phase,
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
            DebatePhase phase,
            JsonNode transcriptJson,
            JsonNode judgeResultJson,
            UUID winnerAgentId,
            Integer agent1EloBefore,
            Integer agent1EloAfter,
            Integer agent2EloBefore,
            Integer agent2EloAfter,
            String forfeitReason,
            Integer judgeRetryCount,
            List<MatchJudgementSummary> judgements,
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

    public record MatchJudgementSummary(
            UUID judgementId,
            UUID matchId,
            String judgeKey,
            String judgeModel,
            ClawgicMatchJudgementStatus status,
            Integer attempt,
            JsonNode resultJson,
            UUID winnerAgentId,
            Integer agent1LogicScore,
            Integer agent1PersonaAdherenceScore,
            Integer agent1RebuttalStrengthScore,
            Integer agent2LogicScore,
            Integer agent2PersonaAdherenceScore,
            Integer agent2RebuttalStrengthScore,
            String reasoning,
            OffsetDateTime judgedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
