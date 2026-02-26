-- Clawgic MVP match lifecycle + transcript storage schema (Step C12)
-- Adds persisted match execution/judging state with JSONB transcript payloads.

CREATE TABLE clawgic_matches (
    match_id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL REFERENCES clawgic_tournaments(tournament_id) ON DELETE CASCADE,
    agent1_id UUID NOT NULL REFERENCES clawgic_agents(agent_id),
    agent2_id UUID NOT NULL REFERENCES clawgic_agents(agent_id),
    status VARCHAR(32) NOT NULL,
    phase VARCHAR(64),
    transcript_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    judge_result_json JSONB,
    winner_agent_id UUID REFERENCES clawgic_agents(agent_id) ON DELETE SET NULL,
    forfeit_reason TEXT,
    judge_retry_count INTEGER NOT NULL DEFAULT 0,
    execution_deadline_at TIMESTAMP WITH TIME ZONE,
    judge_deadline_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    judge_requested_at TIMESTAMP WITH TIME ZONE,
    judged_at TIMESTAMP WITH TIME ZONE,
    forfeited_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_clawgic_matches_distinct_agents CHECK (agent1_id <> agent2_id),
    CONSTRAINT chk_clawgic_matches_status_valid CHECK (
        status IN ('SCHEDULED', 'IN_PROGRESS', 'PENDING_JUDGE', 'COMPLETED', 'FORFEITED')
    ),
    CONSTRAINT chk_clawgic_matches_phase_valid CHECK (
        phase IS NULL OR phase IN (
            'THESIS_DISCOVERY',
            'ARGUMENTATION',
            'COUNTER_ARGUMENTATION',
            'CONCLUSION'
        )
    ),
    CONSTRAINT chk_clawgic_matches_transcript_json_array CHECK (jsonb_typeof(transcript_json) = 'array'),
    CONSTRAINT chk_clawgic_matches_judge_result_json_object CHECK (
        judge_result_json IS NULL OR jsonb_typeof(judge_result_json) = 'object'
    ),
    CONSTRAINT chk_clawgic_matches_winner_agent_valid CHECK (
        winner_agent_id IS NULL OR winner_agent_id = agent1_id OR winner_agent_id = agent2_id
    ),
    CONSTRAINT chk_clawgic_matches_forfeit_reason_not_blank CHECK (
        forfeit_reason IS NULL OR btrim(forfeit_reason) <> ''
    ),
    CONSTRAINT chk_clawgic_matches_judge_retry_count_non_negative CHECK (judge_retry_count >= 0)
);

CREATE INDEX idx_clawgic_matches_tournament_status_created_at
    ON clawgic_matches(tournament_id, status, created_at ASC);
CREATE INDEX idx_clawgic_matches_status_updated_at
    ON clawgic_matches(status, updated_at ASC);
CREATE INDEX idx_clawgic_matches_winner_agent_id
    ON clawgic_matches(winner_agent_id);
