package com.moltrank.clawgic.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "clawgic_matches")
public class ClawgicMatch {

    @Id
    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "agent1_id", nullable = false, updatable = false)
    private UUID agent1Id;

    @Column(name = "agent2_id", nullable = false, updatable = false)
    private UUID agent2Id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClawgicMatchStatus status = ClawgicMatchStatus.SCHEDULED;

    @Column(name = "phase", length = 64)
    private String phase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode transcriptJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "judge_result_json", columnDefinition = "jsonb")
    private JsonNode judgeResultJson;

    @Column(name = "winner_agent_id")
    private UUID winnerAgentId;

    @Column(name = "forfeit_reason", columnDefinition = "TEXT")
    private String forfeitReason;

    @Column(name = "judge_retry_count", nullable = false)
    private Integer judgeRetryCount = 0;

    @Column(name = "execution_deadline_at")
    private OffsetDateTime executionDeadlineAt;

    @Column(name = "judge_deadline_at")
    private OffsetDateTime judgeDeadlineAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "judge_requested_at")
    private OffsetDateTime judgeRequestedAt;

    @Column(name = "judged_at")
    private OffsetDateTime judgedAt;

    @Column(name = "forfeited_at")
    private OffsetDateTime forfeitedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
