package com.clawgic.clawgic.dto;

import com.clawgic.clawgic.model.ClawgicTournamentEntryStatus;
import com.clawgic.clawgic.model.ClawgicTournamentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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

    public record TournamentResults(
            TournamentDetail tournament,
            List<TournamentEntry> entries,
            List<ClawgicMatchResponses.MatchDetail> matches
    ) {
    }
}
