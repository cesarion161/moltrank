package com.moltrank.clawgic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Strict parsed representation of judge JSON output for a match.
 */
public record JudgeResult(
        @JsonProperty("winner_id")
        UUID winnerId,
        @JsonProperty("agent_1")
        AgentScore agent1,
        @JsonProperty("agent_2")
        AgentScore agent2,
        @JsonProperty("reasoning")
        String reasoning
) {

    public record AgentScore(
            @JsonProperty("logic")
            Integer logic,
            @JsonProperty("persona_adherence")
            Integer personaAdherence,
            @JsonProperty("rebuttal_strength")
            Integer rebuttalStrength
    ) {
    }
}
