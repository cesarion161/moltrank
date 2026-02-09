-- MoltRank Database Schema
-- PostgreSQL Migration V1: Initial Schema
-- PRD Reference: Section 9.4

-- Create ENUM types
CREATE TYPE round_status AS ENUM ('open', 'commit', 'reveal', 'settling', 'settled');
CREATE TYPE subscription_type AS ENUM ('realtime', 'free_delay');
CREATE TYPE pair_winner AS ENUM ('A', 'B', 'tie');

-- ============================================================================
-- IDENTITY TABLE
-- Stores user identity information (wallet + X account linkage)
-- ============================================================================
CREATE TABLE identity (
    id SERIAL PRIMARY KEY,
    wallet VARCHAR(44) NOT NULL UNIQUE,  -- Solana wallet address (base58, max 44 chars)
    x_account VARCHAR(255),               -- Twitter/X account handle
    verified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_identity_wallet ON identity(wallet);
CREATE INDEX idx_identity_x_account ON identity(x_account) WHERE x_account IS NOT NULL;

-- ============================================================================
-- MARKET TABLE
-- Represents scoped curation markets by topic/domain
-- ============================================================================
CREATE TABLE market (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    submolt_id VARCHAR(255) NOT NULL,     -- Moltbook submolt identifier
    subscription_revenue BIGINT NOT NULL DEFAULT 0,  -- Total revenue in tokens (lamports)
    subscribers INTEGER NOT NULL DEFAULT 0,
    creation_bond BIGINT NOT NULL DEFAULT 0,
    max_pairs INTEGER NOT NULL DEFAULT 0,  -- Demand-gated pair limit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_market_positive_revenue CHECK (subscription_revenue >= 0),
    CONSTRAINT chk_market_positive_subscribers CHECK (subscribers >= 0),
    CONSTRAINT chk_market_positive_max_pairs CHECK (max_pairs >= 0)
);

CREATE INDEX idx_market_name ON market(name);
CREATE INDEX idx_market_submolt_id ON market(submolt_id);

-- ============================================================================
-- ROUND TABLE
-- Represents curation rounds within markets
-- ============================================================================
CREATE TABLE round (
    id SERIAL PRIMARY KEY,
    market_id INTEGER NOT NULL REFERENCES market(id) ON DELETE CASCADE,
    status round_status NOT NULL DEFAULT 'open',
    pairs INTEGER NOT NULL DEFAULT 0,
    base_per_pair BIGINT NOT NULL DEFAULT 0,      -- Base reward per pair (lamports)
    premium_per_pair BIGINT NOT NULL DEFAULT 0,   -- Market premium per pair (lamports)
    content_merkle_root VARCHAR(64),              -- Merkle root for content provenance
    started_at TIMESTAMP WITH TIME ZONE,
    commit_deadline TIMESTAMP WITH TIME ZONE,
    reveal_deadline TIMESTAMP WITH TIME ZONE,
    settled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_round_positive_pairs CHECK (pairs >= 0),
    CONSTRAINT chk_round_positive_rewards CHECK (base_per_pair >= 0 AND premium_per_pair >= 0)
);

CREATE INDEX idx_round_market_id ON round(market_id);
CREATE INDEX idx_round_status ON round(status);
CREATE INDEX idx_round_market_status ON round(market_id, status);

-- ============================================================================
-- POST TABLE
-- Agent-generated posts to be ranked
-- ============================================================================
CREATE TABLE post (
    id SERIAL PRIMARY KEY,
    moltbook_id VARCHAR(255) NOT NULL UNIQUE,  -- Moltbook post identifier
    market_id INTEGER NOT NULL REFERENCES market(id) ON DELETE CASCADE,
    agent VARCHAR(255) NOT NULL,               -- Agent name/identifier
    content TEXT NOT NULL,
    elo INTEGER NOT NULL DEFAULT 1500,         -- ELO rating, starts at 1500
    matchups INTEGER NOT NULL DEFAULT 0,       -- Total number of pairwise comparisons
    wins INTEGER NOT NULL DEFAULT 0,           -- Number of wins
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_post_positive_matchups CHECK (matchups >= 0),
    CONSTRAINT chk_post_positive_wins CHECK (wins >= 0),
    CONSTRAINT chk_post_wins_le_matchups CHECK (wins <= matchups)
);

CREATE INDEX idx_post_moltbook_id ON post(moltbook_id);
CREATE INDEX idx_post_market_id ON post(market_id);
CREATE INDEX idx_post_elo ON post(elo DESC);
CREATE INDEX idx_post_market_elo ON post(market_id, elo DESC);
CREATE INDEX idx_post_agent ON post(agent);

-- ============================================================================
-- PAIR TABLE
-- Pairwise comparison pairs for curation
-- ============================================================================
CREATE TABLE pair (
    id SERIAL PRIMARY KEY,
    round_id INTEGER NOT NULL REFERENCES round(id) ON DELETE CASCADE,
    post_a INTEGER NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    post_b INTEGER NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    winner pair_winner,                        -- Winner: 'A', 'B', or 'tie' (NULL until settled)
    total_stake BIGINT NOT NULL DEFAULT 0,     -- Total stake across all commitments (lamports)
    reward BIGINT NOT NULL DEFAULT 0,          -- Total reward for majority (lamports)
    is_golden BOOLEAN NOT NULL DEFAULT false,  -- Golden Set calibration pair
    is_audit BOOLEAN NOT NULL DEFAULT false,   -- Audit pair (swapped positions)
    golden_answer pair_winner,                 -- Correct answer for golden set pairs
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_pair_different_posts CHECK (post_a != post_b),
    CONSTRAINT chk_pair_positive_stake CHECK (total_stake >= 0),
    CONSTRAINT chk_pair_positive_reward CHECK (reward >= 0),
    CONSTRAINT chk_pair_golden_answer CHECK (
        (is_golden = false AND golden_answer IS NULL) OR
        (is_golden = true AND golden_answer IS NOT NULL)
    )
);

CREATE INDEX idx_pair_round_id ON pair(round_id);
CREATE INDEX idx_pair_post_a ON pair(post_a);
CREATE INDEX idx_pair_post_b ON pair(post_b);
CREATE INDEX idx_pair_is_golden ON pair(is_golden) WHERE is_golden = true;
CREATE INDEX idx_pair_is_audit ON pair(is_audit) WHERE is_audit = true;
CREATE INDEX idx_pair_winner ON pair(winner) WHERE winner IS NOT NULL;

-- ============================================================================
-- COMMITMENT TABLE
-- Curator commitments (commit-reveal voting)
-- ============================================================================
CREATE TABLE commitment (
    id SERIAL PRIMARY KEY,
    pair_id INTEGER NOT NULL REFERENCES pair(id) ON DELETE CASCADE,
    curator_wallet VARCHAR(44) NOT NULL REFERENCES identity(wallet) ON DELETE CASCADE,
    hash VARCHAR(66) NOT NULL,                 -- keccak256 hash (0x + 64 hex chars)
    stake BIGINT NOT NULL,                     -- Stake amount (lamports)
    encrypted_reveal TEXT NOT NULL,            -- Encrypted reveal payload
    revealed BOOLEAN NOT NULL DEFAULT false,
    choice pair_winner,                        -- Revealed choice: 'A', 'B', or 'tie'
    nonce VARCHAR(64),                         -- Commitment nonce (revealed)
    committed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revealed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_commitment_positive_stake CHECK (stake > 0),
    CONSTRAINT chk_commitment_revealed_choice CHECK (
        (revealed = false AND choice IS NULL) OR
        (revealed = true AND choice IS NOT NULL)
    ),
    CONSTRAINT uq_commitment_pair_curator UNIQUE (pair_id, curator_wallet)
);

CREATE INDEX idx_commitment_pair_id ON commitment(pair_id);
CREATE INDEX idx_commitment_curator_wallet ON commitment(curator_wallet);
CREATE INDEX idx_commitment_revealed ON commitment(revealed);
CREATE INDEX idx_commitment_pair_revealed ON commitment(pair_id, revealed);

-- ============================================================================
-- CURATOR TABLE
-- Curator reputation and statistics per market
-- ============================================================================
CREATE TABLE curator (
    wallet VARCHAR(44) NOT NULL REFERENCES identity(wallet) ON DELETE CASCADE,
    identity_id INTEGER NOT NULL REFERENCES identity(id) ON DELETE CASCADE,
    market_id INTEGER NOT NULL REFERENCES market(id) ON DELETE CASCADE,
    earned BIGINT NOT NULL DEFAULT 0,                      -- Total tokens earned (lamports)
    lost BIGINT NOT NULL DEFAULT 0,                        -- Total tokens lost (lamports)
    curator_score DECIMAL(10, 4) NOT NULL DEFAULT 0.0,     -- Composite reputation score
    calibration_rate DECIMAL(5, 4) NOT NULL DEFAULT 0.0,   -- Golden Set accuracy (0.0-1.0)
    audit_pass_rate DECIMAL(5, 4) NOT NULL DEFAULT 0.0,    -- Audit pair consistency (0.0-1.0)
    alignment_stability DECIMAL(5, 4) NOT NULL DEFAULT 0.0,-- Consensus alignment stability (0.0-1.0)
    fraud_flags INTEGER NOT NULL DEFAULT 0,                -- Number of fraud flags
    pairs_this_epoch INTEGER NOT NULL DEFAULT 0,           -- Pairs evaluated in current epoch
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (wallet, market_id),
    CONSTRAINT chk_curator_positive_earned CHECK (earned >= 0),
    CONSTRAINT chk_curator_positive_lost CHECK (lost >= 0),
    CONSTRAINT chk_curator_rates_valid CHECK (
        calibration_rate >= 0.0 AND calibration_rate <= 1.0 AND
        audit_pass_rate >= 0.0 AND audit_pass_rate <= 1.0 AND
        alignment_stability >= 0.0 AND alignment_stability <= 1.0
    ),
    CONSTRAINT chk_curator_positive_fraud_flags CHECK (fraud_flags >= 0),
    CONSTRAINT chk_curator_positive_pairs CHECK (pairs_this_epoch >= 0)
);

CREATE INDEX idx_curator_wallet ON curator(wallet);
CREATE INDEX idx_curator_market_id ON curator(market_id);
CREATE INDEX idx_curator_score ON curator(curator_score DESC);
CREATE INDEX idx_curator_market_score ON curator(market_id, curator_score DESC);

-- ============================================================================
-- SUBSCRIPTION TABLE
-- Reader subscriptions to markets
-- ============================================================================
CREATE TABLE subscription (
    id SERIAL PRIMARY KEY,
    reader_wallet VARCHAR(44) NOT NULL,        -- Reader's wallet address
    market_id INTEGER NOT NULL REFERENCES market(id) ON DELETE CASCADE,
    amount BIGINT NOT NULL,                    -- Subscription amount (lamports)
    type subscription_type NOT NULL,
    round_id INTEGER REFERENCES round(id) ON DELETE SET NULL,  -- Round when subscribed
    subscribed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_subscription_positive_amount CHECK (amount > 0)
);

CREATE INDEX idx_subscription_reader_wallet ON subscription(reader_wallet);
CREATE INDEX idx_subscription_market_id ON subscription(market_id);
CREATE INDEX idx_subscription_type ON subscription(type);
CREATE INDEX idx_subscription_round_id ON subscription(round_id) WHERE round_id IS NOT NULL;

-- ============================================================================
-- GOLDEN_SET_ITEM TABLE
-- Pre-labeled pairs for calibration
-- ============================================================================
CREATE TABLE golden_set_item (
    id SERIAL PRIMARY KEY,
    post_a INTEGER NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    post_b INTEGER NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    correct_answer pair_winner NOT NULL,      -- Pre-determined correct answer
    confidence DECIMAL(5, 4) NOT NULL,        -- Confidence level (0.0-1.0)
    source VARCHAR(255) NOT NULL,             -- Source of ground truth (e.g., 'expert_panel', 'consensus')
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_golden_different_posts CHECK (post_a != post_b),
    CONSTRAINT chk_golden_confidence_valid CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

CREATE INDEX idx_golden_set_post_a ON golden_set_item(post_a);
CREATE INDEX idx_golden_set_post_b ON golden_set_item(post_b);

-- ============================================================================
-- GLOBAL_POOL TABLE
-- Single-row table for global pool state
-- ============================================================================
CREATE TABLE global_pool (
    id INTEGER PRIMARY KEY DEFAULT 1,          -- Enforce single row
    balance BIGINT NOT NULL DEFAULT 0,         -- Current pool balance (lamports)
    alpha DECIMAL(5, 4) NOT NULL DEFAULT 0.30, -- Base reward split ratio (α)
    round_id INTEGER REFERENCES round(id) ON DELETE SET NULL,  -- Current round
    settlement_hash VARCHAR(66),               -- Latest settlement hash
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_global_pool_single_row CHECK (id = 1),
    CONSTRAINT chk_global_pool_positive_balance CHECK (balance >= 0),
    CONSTRAINT chk_global_pool_alpha_valid CHECK (alpha >= 0.0 AND alpha <= 1.0)
);

-- Insert initial global pool row
INSERT INTO global_pool (id, balance, alpha) VALUES (1, 0, 0.30);

CREATE INDEX idx_global_pool_round_id ON global_pool(round_id) WHERE round_id IS NOT NULL;

-- ============================================================================
-- TRIGGERS FOR UPDATED_AT
-- Automatically update updated_at timestamps
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_identity_updated_at BEFORE UPDATE ON identity
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_market_updated_at BEFORE UPDATE ON market
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_round_updated_at BEFORE UPDATE ON round
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_post_updated_at BEFORE UPDATE ON post
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_curator_updated_at BEFORE UPDATE ON curator
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_global_pool_updated_at BEFORE UPDATE ON global_pool
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- COMMENTS
-- Table and column documentation
-- ============================================================================
COMMENT ON TABLE identity IS 'User identity records linking wallet addresses to X (Twitter) accounts';
COMMENT ON TABLE market IS 'Scoped curation markets by topic/domain';
COMMENT ON TABLE round IS 'Curation rounds with commit-reveal phases';
COMMENT ON TABLE post IS 'Agent-generated posts with ELO rankings';
COMMENT ON TABLE pair IS 'Pairwise comparison pairs for curation';
COMMENT ON TABLE commitment IS 'Curator vote commitments with commit-reveal mechanism';
COMMENT ON TABLE curator IS 'Curator reputation and performance statistics per market';
COMMENT ON TABLE subscription IS 'Reader subscriptions to market feeds';
COMMENT ON TABLE golden_set_item IS 'Pre-labeled calibration pairs with ground truth answers';
COMMENT ON TABLE global_pool IS 'Single-row table tracking global reward pool state';

COMMENT ON COLUMN pair.is_golden IS 'True if this pair is a Golden Set calibration pair with a pre-determined correct answer';
COMMENT ON COLUMN pair.is_audit IS 'True if this pair is an Audit Pair (swapped repeat) to detect position-based voting';
COMMENT ON COLUMN commitment.encrypted_reveal IS 'Encrypted reveal payload for auto-reveal mechanism';
COMMENT ON COLUMN curator.curator_score IS 'Composite reputation score: w1*calibration + w2*stability + w3*audit_pass - w4*fraud_flags';
COMMENT ON COLUMN global_pool.alpha IS 'Base reward split ratio (α): proportion of pool allocated to universal base rewards';
