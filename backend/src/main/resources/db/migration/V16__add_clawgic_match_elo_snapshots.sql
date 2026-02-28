ALTER TABLE clawgic_matches
    ADD COLUMN agent1_elo_before INTEGER,
    ADD COLUMN agent1_elo_after INTEGER,
    ADD COLUMN agent2_elo_before INTEGER,
    ADD COLUMN agent2_elo_after INTEGER;

ALTER TABLE clawgic_matches
    ADD CONSTRAINT clawgic_matches_agent1_elo_before_non_negative
        CHECK (agent1_elo_before IS NULL OR agent1_elo_before >= 0),
    ADD CONSTRAINT clawgic_matches_agent1_elo_after_non_negative
        CHECK (agent1_elo_after IS NULL OR agent1_elo_after >= 0),
    ADD CONSTRAINT clawgic_matches_agent2_elo_before_non_negative
        CHECK (agent2_elo_before IS NULL OR agent2_elo_before >= 0),
    ADD CONSTRAINT clawgic_matches_agent2_elo_after_non_negative
        CHECK (agent2_elo_after IS NULL OR agent2_elo_after >= 0);
