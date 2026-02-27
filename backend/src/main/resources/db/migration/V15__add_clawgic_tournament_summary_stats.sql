-- Step C29: persist tournament-level bracket outcome summary stats.
-- These counters are derived from terminal match states at tournament completion.

ALTER TABLE clawgic_tournaments
    ADD COLUMN matches_completed INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN matches_forfeited INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_clawgic_tournaments_matches_completed_non_negative CHECK (matches_completed >= 0),
    ADD CONSTRAINT chk_clawgic_tournaments_matches_forfeited_non_negative CHECK (matches_forfeited >= 0),
    ADD CONSTRAINT chk_clawgic_tournaments_match_summary_le_total CHECK (
        matches_completed + matches_forfeited <= (bracket_size - 1)
    );
