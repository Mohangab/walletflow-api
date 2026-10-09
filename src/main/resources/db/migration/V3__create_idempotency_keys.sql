-- Stored outcomes of money-moving requests, keyed by (user, Idempotency-Key).
-- The row is written in the same database transaction as the ledger posting, so a request is
-- either fully applied *and* recorded, or neither.
CREATE TABLE idempotency_keys (
    id               UUID          PRIMARY KEY,
    user_id          UUID          NOT NULL REFERENCES users (id),
    idempotency_key  VARCHAR(100)  NOT NULL,
    request_hash     VARCHAR(64)   NOT NULL,
    response_status  INTEGER       NOT NULL,
    response_body    VARCHAR(8000) NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_idempotency_user_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);
