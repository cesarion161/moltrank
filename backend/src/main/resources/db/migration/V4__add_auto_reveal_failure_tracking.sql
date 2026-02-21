ALTER TABLE commitment
    ADD COLUMN auto_reveal_failed BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN auto_reveal_failure_reason VARCHAR(64),
    ADD COLUMN auto_reveal_failed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_commitment_auto_reveal_failed
    ON commitment(auto_reveal_failed)
    WHERE auto_reveal_failed = true;
