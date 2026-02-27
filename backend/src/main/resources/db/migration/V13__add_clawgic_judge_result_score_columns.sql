-- Clawgic MVP judge verdict persistence schema (Step C25)
-- Stores one validated JSON verdict per judge attempt, per match.

CREATE TABLE clawgic_match_judgements (
    judgement_id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES clawgic_matches(match_id) ON DELETE CASCADE,
    tournament_id UUID NOT NULL REFERENCES clawgic_tournaments(tournament_id) ON DELETE CASCADE,
    judge_key VARCHAR(128) NOT NULL,
    judge_model VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt INTEGER NOT NULL DEFAULT 1,
    result_json JSONB NOT NULL,
    winner_agent_id UUID REFERENCES clawgic_agents(agent_id) ON DELETE SET NULL,
    agent1_logic_score INTEGER,
    agent1_persona_adherence_score INTEGER,
    agent1_rebuttal_strength_score INTEGER,
    agent2_logic_score INTEGER,
    agent2_persona_adherence_score INTEGER,
    agent2_rebuttal_strength_score INTEGER,
    reasoning TEXT,
    judged_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_clawgic_match_judgements_match_judge_attempt
        UNIQUE (match_id, judge_key, attempt),
    CONSTRAINT chk_clawgic_match_judgements_status_valid CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'ERROR')
    ),
    CONSTRAINT chk_clawgic_match_judgements_attempt_positive CHECK (attempt > 0),
    CONSTRAINT chk_clawgic_match_judgements_result_json_object CHECK (
        jsonb_typeof(result_json) = 'object'
    ),
    CONSTRAINT chk_clawgic_match_judgements_agent1_logic_score_range CHECK (
        agent1_logic_score IS NULL OR agent1_logic_score BETWEEN 0 AND 10
    ),
    CONSTRAINT chk_clawgic_match_judgements_agent1_persona_adherence_score_range CHECK (
        agent1_persona_adherence_score IS NULL OR agent1_persona_adherence_score BETWEEN 0 AND 10
    ),
    CONSTRAINT chk_clawgic_match_judgements_agent1_rebuttal_strength_score_range CHECK (
        agent1_rebuttal_strength_score IS NULL OR agent1_rebuttal_strength_score BETWEEN 0 AND 10
    ),
    CONSTRAINT chk_clawgic_match_judgements_agent2_logic_score_range CHECK (
        agent2_logic_score IS NULL OR agent2_logic_score BETWEEN 0 AND 10
    ),
    CONSTRAINT chk_clawgic_match_judgements_agent2_persona_adherence_score_range CHECK (
        agent2_persona_adherence_score IS NULL OR agent2_persona_adherence_score BETWEEN 0 AND 10
    ),
    CONSTRAINT chk_clawgic_match_judgements_agent2_rebuttal_strength_score_range CHECK (
        agent2_rebuttal_strength_score IS NULL OR agent2_rebuttal_strength_score BETWEEN 0 AND 10
    ),
    CONSTRAINT chk_clawgic_match_judgements_scores_all_or_none CHECK (
        (
            agent1_logic_score IS NULL
            AND agent1_persona_adherence_score IS NULL
            AND agent1_rebuttal_strength_score IS NULL
            AND agent2_logic_score IS NULL
            AND agent2_persona_adherence_score IS NULL
            AND agent2_rebuttal_strength_score IS NULL
        )
        OR
        (
            agent1_logic_score IS NOT NULL
            AND agent1_persona_adherence_score IS NOT NULL
            AND agent1_rebuttal_strength_score IS NOT NULL
            AND agent2_logic_score IS NOT NULL
            AND agent2_persona_adherence_score IS NOT NULL
            AND agent2_rebuttal_strength_score IS NOT NULL
        )
    ),
    CONSTRAINT chk_clawgic_match_judgements_accepted_requires_scores CHECK (
        status <> 'ACCEPTED'
        OR (
            winner_agent_id IS NOT NULL
            AND agent1_logic_score IS NOT NULL
            AND agent1_persona_adherence_score IS NOT NULL
            AND agent1_rebuttal_strength_score IS NOT NULL
            AND agent2_logic_score IS NOT NULL
            AND agent2_persona_adherence_score IS NOT NULL
            AND agent2_rebuttal_strength_score IS NOT NULL
        )
    )
);

CREATE INDEX idx_clawgic_match_judgements_match_created_at
    ON clawgic_match_judgements(match_id, created_at ASC);

CREATE INDEX idx_clawgic_match_judgements_match_status_created_at
    ON clawgic_match_judgements(match_id, status, created_at ASC);

CREATE INDEX idx_clawgic_match_judgements_winner_agent_id
    ON clawgic_match_judgements(winner_agent_id);
