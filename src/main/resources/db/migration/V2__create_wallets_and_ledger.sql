-- Wallets hold a cached balance that is always derivable from ledger_entries.
-- USER wallets can never go negative; SYSTEM wallets represent the outside world
-- (bank rails / card networks) and mirror every deposit and withdrawal.
CREATE TABLE wallets (
    id         UUID          PRIMARY KEY,
    owner_id   UUID          REFERENCES users (id),
    kind       VARCHAR(10)   NOT NULL,
    currency   VARCHAR(3)    NOT NULL,
    balance    NUMERIC(19,2) NOT NULL DEFAULT 0,
    status     VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    version    BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_wallets_kind CHECK (kind IN ('USER', 'SYSTEM')),
    CONSTRAINT ck_wallets_status CHECK (status IN ('ACTIVE', 'FROZEN')),
    CONSTRAINT ck_wallets_owner CHECK ((kind = 'USER' AND owner_id IS NOT NULL) OR (kind = 'SYSTEM' AND owner_id IS NULL)),
    CONSTRAINT ck_wallets_non_negative CHECK (kind = 'SYSTEM' OR balance >= 0),
    CONSTRAINT uk_wallets_owner_currency UNIQUE (owner_id, currency)
);

CREATE INDEX idx_wallets_owner ON wallets (owner_id);

-- One business event (deposit, withdrawal, transfer) = one ledger transaction.
CREATE TABLE ledger_transactions (
    id                    UUID          PRIMARY KEY,
    type                  VARCHAR(20)   NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    amount                NUMERIC(19,2) NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    source_wallet_id      UUID          NOT NULL REFERENCES wallets (id),
    destination_wallet_id UUID          NOT NULL REFERENCES wallets (id),
    description           VARCHAR(255),
    initiated_by          UUID          NOT NULL REFERENCES users (id),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ledger_tx_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_ledger_tx_amount CHECK (amount > 0),
    CONSTRAINT ck_ledger_tx_wallets CHECK (source_wallet_id <> destination_wallet_id)
);

CREATE INDEX idx_ledger_tx_created ON ledger_transactions (created_at);

-- Append-only double-entry lines. Every transaction has entries whose debits equal its credits.
-- Convention: CREDIT increases a wallet balance, DEBIT decreases it.
CREATE TABLE ledger_entries (
    id             UUID          PRIMARY KEY,
    transaction_id UUID          NOT NULL REFERENCES ledger_transactions (id),
    wallet_id      UUID          NOT NULL REFERENCES wallets (id),
    direction      VARCHAR(6)    NOT NULL,
    amount         NUMERIC(19,2) NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    balance_after  NUMERIC(19,2) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ledger_entries_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entries_amount CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entries_wallet_created ON ledger_entries (wallet_id, created_at);
CREATE INDEX idx_ledger_entries_transaction ON ledger_entries (transaction_id);

-- Settlement accounts for every supported currency. A currency is "supported" iff it has one.
INSERT INTO wallets (id, owner_id, kind, currency, balance, status, version, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000001', NULL, 'SYSTEM', 'INR', 0, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', NULL, 'SYSTEM', 'USD', 0, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', NULL, 'SYSTEM', 'EUR', 0, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000004', NULL, 'SYSTEM', 'GBP', 0, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
