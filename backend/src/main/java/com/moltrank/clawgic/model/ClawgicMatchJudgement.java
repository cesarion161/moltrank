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
@Table(name = "clawgic_match_judgements")
public class ClawgicMatchJudgement {

    @Id
    @Column(name = "judgement_id", nullable = false, updatable = false)
    private UUID judgementId;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "judge_key", nullable = false, length = 128)
    private String judgeKey;

    @Column(name = "judge_model", length = 128)
    private String judgeModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClawgicMatchJudgementStatus status = ClawgicMatchJudgementStatus.PENDING;

    @Column(name = "attempt", nullable = false)
    private Integer attempt = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode resultJson;

    @Column(name = "winner_agent_id")
    private UUID winnerAgentId;

    @Column(name = "agent1_logic_score")
    private Integer agent1LogicScore;

    @Column(name = "agent1_persona_adherence_score")
    private Integer agent1PersonaAdherenceScore;

    @Column(name = "agent1_rebuttal_strength_score")
    private Integer agent1RebuttalStrengthScore;

    @Column(name = "agent2_logic_score")
    private Integer agent2LogicScore;

    @Column(name = "agent2_persona_adherence_score")
    private Integer agent2PersonaAdherenceScore;

    @Column(name = "agent2_rebuttal_strength_score")
    private Integer agent2RebuttalStrengthScore;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "judged_at")
    private OffsetDateTime judgedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
