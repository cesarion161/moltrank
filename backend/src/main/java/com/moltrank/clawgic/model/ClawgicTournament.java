package com.moltrank.clawgic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "clawgic_tournaments")
public class ClawgicTournament {

    @Id
    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "topic", nullable = false, columnDefinition = "TEXT")
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClawgicTournamentStatus status = ClawgicTournamentStatus.SCHEDULED;

    @Column(name = "bracket_size", nullable = false)
    private Integer bracketSize = 4;

    @Column(name = "max_entries", nullable = false)
    private Integer maxEntries = 4;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "entry_close_time", nullable = false)
    private OffsetDateTime entryCloseTime;

    @Column(name = "base_entry_fee_usdc", nullable = false, precision = 18, scale = 6)
    private BigDecimal baseEntryFeeUsdc = BigDecimal.ZERO;

    @Column(name = "winner_agent_id")
    private UUID winnerAgentId;

    @Column(name = "matches_completed", nullable = false)
    private Integer matchesCompleted = 0;

    @Column(name = "matches_forfeited", nullable = false)
    private Integer matchesForfeited = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
