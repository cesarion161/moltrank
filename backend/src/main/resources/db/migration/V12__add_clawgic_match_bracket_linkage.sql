-- Clawgic MVP deterministic 4-agent bracket linkage metadata (Step C19)
-- Allows semifinal -> final progression wiring and final placeholder creation.

ALTER TABLE clawgic_matches
    ADD COLUMN bracket_round INTEGER,
    ADD COLUMN bracket_position INTEGER,
    ADD COLUMN next_match_id UUID REFERENCES clawgic_matches(match_id) ON DELETE SET NULL,
    ADD COLUMN next_match_agent_slot INTEGER;

-- Final placeholder rows may not yet have participants assigned.
ALTER TABLE clawgic_matches
    ALTER COLUMN agent1_id DROP NOT NULL,
    ALTER COLUMN agent2_id DROP NOT NULL;

ALTER TABLE clawgic_matches
    DROP CONSTRAINT chk_clawgic_matches_distinct_agents,
    ADD CONSTRAINT chk_clawgic_matches_distinct_agents CHECK (
        agent1_id IS NULL OR agent2_id IS NULL OR agent1_id <> agent2_id
    );

ALTER TABLE clawgic_matches
    DROP CONSTRAINT chk_clawgic_matches_winner_agent_valid,
    ADD CONSTRAINT chk_clawgic_matches_winner_agent_valid CHECK (
        winner_agent_id IS NULL OR winner_agent_id = agent1_id OR winner_agent_id = agent2_id
    );

ALTER TABLE clawgic_matches
    ADD CONSTRAINT chk_clawgic_matches_bracket_round_positive CHECK (
        bracket_round IS NULL OR bracket_round > 0
    ),
    ADD CONSTRAINT chk_clawgic_matches_bracket_position_positive CHECK (
        bracket_position IS NULL OR bracket_position > 0
    ),
    ADD CONSTRAINT chk_clawgic_matches_next_slot_valid CHECK (
        next_match_agent_slot IS NULL OR next_match_agent_slot IN (1, 2)
    ),
    ADD CONSTRAINT chk_clawgic_matches_next_link_consistent CHECK (
        (next_match_id IS NULL AND next_match_agent_slot IS NULL)
        OR (next_match_id IS NOT NULL AND next_match_agent_slot IS NOT NULL)
    ),
    ADD CONSTRAINT chk_clawgic_matches_next_match_not_self CHECK (
        next_match_id IS NULL OR next_match_id <> match_id
    );

CREATE INDEX idx_clawgic_matches_next_match_id
    ON clawgic_matches(next_match_id);

CREATE UNIQUE INDEX idx_clawgic_matches_tournament_round_position
    ON clawgic_matches(tournament_id, bracket_round, bracket_position)
    WHERE bracket_round IS NOT NULL AND bracket_position IS NOT NULL;
