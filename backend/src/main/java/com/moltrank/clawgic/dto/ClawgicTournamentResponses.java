package com.moltrank.clawgic.dto;

import com.moltrank.clawgic.model.ClawgicTournamentEntryStatus;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ClawgicTournamentResponses {

    private ClawgicTournamentResponses() {
    }

    public record TournamentSummary(
            UUID tournamentId,
            String topic,
            ClawgicTournamentStatus status,
            Integer bracketSize,
            Integer maxEntries,
            OffsetDateTime startTime,
            OffsetDateTime entryCloseTime,
            BigDecimal baseEntryFeeUsdc,
            UUID winnerAgentId,
            Integer matchesCompleted,
            Integer matchesForfeited,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record TournamentDetail(
            UUID tournamentId,
            String topic,
            ClawgicTournamentStatus status,
            Integer bracketSize,
            Integer maxEntries,
            OffsetDateTime startTime,
            OffsetDateTime entryCloseTime,
            BigDecimal baseEntryFeeUsdc,
            UUID winnerAgentId,
            Integer matchesCompleted,
            Integer matchesForfeited,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt
    ) {
    }

    public record TournamentEntry(
            UUID entryId,
            UUID tournamentId,
            UUID agentId,
            String walletAddress,
            ClawgicTournamentEntryStatus status,
            Integer seedPosition,
            Integer seedSnapshotElo,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
