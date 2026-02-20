-- Persist curator skip actions so skipped pairs are not reassigned.

CREATE TABLE IF NOT EXISTS pair_skip (
    id SERIAL PRIMARY KEY,
    pair_id INTEGER NOT NULL REFERENCES pair(id) ON DELETE CASCADE,
    curator_wallet VARCHAR(44) NOT NULL REFERENCES identity(wallet) ON DELETE CASCADE,
    skipped_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pair_skip_pair_curator UNIQUE (pair_id, curator_wallet)
);

CREATE INDEX IF NOT EXISTS idx_pair_skip_pair_id ON pair_skip(pair_id);
CREATE INDEX IF NOT EXISTS idx_pair_skip_curator_wallet ON pair_skip(curator_wallet);
