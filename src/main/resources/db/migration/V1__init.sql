CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    type VARCHAR(32) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount >= 0),
    currency VARCHAR(16) NOT NULL DEFAULT 'PTS',
    idempotency_key VARCHAR(128) NOT NULL,
    related_account_id UUID,
    reference_transaction_id UUID REFERENCES ledger_transactions(id),
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    primary_transaction_id UUID REFERENCES ledger_transactions(id),
    secondary_transaction_id UUID REFERENCES ledger_transactions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_idempotency UNIQUE (account_id, operation, idempotency_key)
);

CREATE INDEX idx_transactions_account_created_desc
ON ledger_transactions (account_id, created_at DESC, id DESC);

CREATE INDEX idx_transactions_reference
ON ledger_transactions (reference_transaction_id);