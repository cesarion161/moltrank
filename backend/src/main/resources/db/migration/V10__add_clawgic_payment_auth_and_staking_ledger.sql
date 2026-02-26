-- Clawgic MVP payment authorization tracking + staking ledger schema (Step C13)
-- Adds x402 payment authorization audit/replay guards and off-chain accounting
-- rows used by tournament entry + settlement flows.

CREATE TABLE clawgic_payment_authorizations (
    payment_authorization_id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL REFERENCES clawgic_tournaments(tournament_id) ON DELETE CASCADE,
    entry_id UUID REFERENCES clawgic_tournament_entries(entry_id) ON DELETE SET NULL,
    agent_id UUID NOT NULL REFERENCES clawgic_agents(agent_id) ON DELETE CASCADE,
    wallet_address VARCHAR(128) NOT NULL REFERENCES clawgic_users(wallet_address) ON DELETE CASCADE,
    request_nonce VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    authorization_nonce VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    payment_header_json JSONB,
    amount_authorized_usdc NUMERIC(18, 6) NOT NULL DEFAULT 0,
    chain_id BIGINT,
    recipient_wallet_address VARCHAR(128),
    failure_reason TEXT,
    challenge_expires_at TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_clawgic_payment_auth_request_nonce_not_blank CHECK (btrim(request_nonce) <> ''),
    CONSTRAINT chk_clawgic_payment_auth_idempotency_key_not_blank CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT chk_clawgic_payment_auth_status_valid CHECK (
        status IN (
            'CHALLENGED',
            'PENDING_VERIFICATION',
            'AUTHORIZED',
            'BYPASSED',
            'REJECTED',
            'REPLAY_REJECTED',
            'EXPIRED'
        )
    ),
    CONSTRAINT chk_clawgic_payment_auth_json_object CHECK (
        payment_header_json IS NULL OR jsonb_typeof(payment_header_json) = 'object'
    ),
    CONSTRAINT chk_clawgic_payment_auth_amount_non_negative CHECK (amount_authorized_usdc >= 0),
    CONSTRAINT chk_clawgic_payment_auth_failure_reason_not_blank CHECK (
        failure_reason IS NULL OR btrim(failure_reason) <> ''
    )
);

ALTER TABLE clawgic_payment_authorizations
    ADD CONSTRAINT uq_clawgic_payment_auth_wallet_request_nonce UNIQUE (wallet_address, request_nonce);

ALTER TABLE clawgic_payment_authorizations
    ADD CONSTRAINT uq_clawgic_payment_auth_wallet_idempotency_key UNIQUE (wallet_address, idempotency_key);

CREATE UNIQUE INDEX uq_clawgic_payment_auth_wallet_authorization_nonce
    ON clawgic_payment_authorizations(wallet_address, authorization_nonce)
    WHERE authorization_nonce IS NOT NULL;

CREATE INDEX idx_clawgic_payment_auth_tournament_status_created_at
    ON clawgic_payment_authorizations(tournament_id, status, created_at ASC);
CREATE INDEX idx_clawgic_payment_auth_entry_id
    ON clawgic_payment_authorizations(entry_id);
CREATE INDEX idx_clawgic_payment_auth_status_updated_at
    ON clawgic_payment_authorizations(status, updated_at ASC);

CREATE TABLE clawgic_staking_ledger (
    stake_id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL REFERENCES clawgic_tournaments(tournament_id) ON DELETE CASCADE,
    entry_id UUID REFERENCES clawgic_tournament_entries(entry_id) ON DELETE SET NULL,
    payment_authorization_id UUID REFERENCES clawgic_payment_authorizations(payment_authorization_id) ON DELETE SET NULL,
    agent_id UUID NOT NULL REFERENCES clawgic_agents(agent_id) ON DELETE CASCADE,
    wallet_address VARCHAR(128) NOT NULL REFERENCES clawgic_users(wallet_address) ON DELETE CASCADE,
    amount_staked NUMERIC(18, 6) NOT NULL DEFAULT 0,
    judge_fee_deducted NUMERIC(18, 6) NOT NULL DEFAULT 0,
    system_retention NUMERIC(18, 6) NOT NULL DEFAULT 0,
    reward_payout NUMERIC(18, 6) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    settlement_note TEXT,
    authorized_at TIMESTAMP WITH TIME ZONE,
    entered_at TIMESTAMP WITH TIME ZONE,
    locked_at TIMESTAMP WITH TIME ZONE,
    forfeited_at TIMESTAMP WITH TIME ZONE,
    settled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_clawgic_staking_ledger_status_valid CHECK (
        status IN ('AUTHORIZED', 'ENTERED', 'LOCKED', 'SETTLED', 'FORFEITED', 'CANCELLED')
    ),
    CONSTRAINT chk_clawgic_staking_ledger_amounts_non_negative CHECK (
        amount_staked >= 0
        AND judge_fee_deducted >= 0
        AND system_retention >= 0
        AND reward_payout >= 0
    ),
    CONSTRAINT chk_clawgic_staking_ledger_note_not_blank CHECK (
        settlement_note IS NULL OR btrim(settlement_note) <> ''
    ),
    CONSTRAINT uq_clawgic_staking_ledger_tournament_agent UNIQUE (tournament_id, agent_id)
);

CREATE UNIQUE INDEX uq_clawgic_staking_ledger_entry_id
    ON clawgic_staking_ledger(entry_id)
    WHERE entry_id IS NOT NULL;

CREATE UNIQUE INDEX uq_clawgic_staking_ledger_payment_authorization_id
    ON clawgic_staking_ledger(payment_authorization_id)
    WHERE payment_authorization_id IS NOT NULL;

CREATE INDEX idx_clawgic_staking_ledger_tournament_status
    ON clawgic_staking_ledger(tournament_id, status);
CREATE INDEX idx_clawgic_staking_ledger_wallet_status
    ON clawgic_staking_ledger(wallet_address, status);
CREATE INDEX idx_clawgic_staking_ledger_settled_at
    ON clawgic_staking_ledger(settled_at);
