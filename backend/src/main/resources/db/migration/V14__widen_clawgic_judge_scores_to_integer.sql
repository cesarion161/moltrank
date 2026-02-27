-- Cleanup migration for local iterations before C25 was finalized.
-- On fresh schemas this is a no-op; on pre-final local schemas it removes
-- obsolete per-match parsed score columns in favor of clawgic_match_judgements.

ALTER TABLE clawgic_matches
    DROP CONSTRAINT IF EXISTS chk_clawgic_matches_judge_agent1_logic_score_range,
    DROP CONSTRAINT IF EXISTS chk_clawgic_matches_judge_agent1_persona_adherence_score_range,
    DROP CONSTRAINT IF EXISTS chk_clawgic_matches_judge_agent1_rebuttal_strength_score_range,
    DROP CONSTRAINT IF EXISTS chk_clawgic_matches_judge_agent2_logic_score_range,
    DROP CONSTRAINT IF EXISTS chk_clawgic_matches_judge_agent2_persona_adherence_score_range,
    DROP CONSTRAINT IF EXISTS chk_clawgic_matches_judge_agent2_rebuttal_strength_score_range,
    DROP CONSTRAINT IF EXISTS chk_clawgic_matches_judge_scores_all_or_none,
    DROP CONSTRAINT IF EXISTS chk_clawgic_matches_judge_scores_require_winner,
    DROP CONSTRAINT IF EXISTS chk_clawgic_matches_judge_scores_require_judge_json;

ALTER TABLE clawgic_matches
    DROP COLUMN IF EXISTS judge_agent1_logic_score,
    DROP COLUMN IF EXISTS judge_agent1_persona_adherence_score,
    DROP COLUMN IF EXISTS judge_agent1_rebuttal_strength_score,
    DROP COLUMN IF EXISTS judge_agent2_logic_score,
    DROP COLUMN IF EXISTS judge_agent2_persona_adherence_score,
    DROP COLUMN IF EXISTS judge_agent2_rebuttal_strength_score;
