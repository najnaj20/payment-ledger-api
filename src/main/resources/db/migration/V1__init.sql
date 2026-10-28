-- Payment ledger schema. Amounts are BIGINT minor units, never floats.
-- Balances are derived from ledger_entries; accounts carry no balance column.

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    owner VARCHAR(255) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    from_account_id UUID NOT NULL REFERENCES accounts (id),
    to_account_id UUID NOT NULL REFERENCES accounts (id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (from_account_id <> to_account_id)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL REFERENCES transfers (id),
    account_id UUID NOT NULL REFERENCES accounts (id),
    type VARCHAR(10) NOT NULL CHECK (type IN ('DEBIT', 'CREDIT')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entries_account ON ledger_entries (account_id, created_at);
CREATE INDEX idx_ledger_entries_transfer ON ledger_entries (transfer_id);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    request_hash CHAR(64) NOT NULL,
    transfer_id UUID NOT NULL REFERENCES transfers (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
