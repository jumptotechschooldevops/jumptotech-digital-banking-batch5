-- JumpToTech Digital Banking - initial schema
-- Money is always stored as integer cents (BIGINT). Never floating point.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================================
-- users
-- =========================================================================
-- email is stored already lowercased by the application (see auth.EmailNormalizer),
-- so a plain unique TEXT column gives case-insensitive uniqueness without requiring
-- the citext extension (which also complicates Hibernate schema validation).
CREATE TABLE users (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                  TEXT NOT NULL UNIQUE,
    password_hash          TEXT NOT NULL,
    role                   TEXT NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    status                 TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    failed_login_attempts  INT NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================================================================
-- customer_profiles
-- =========================================================================
CREATE TABLE customer_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    first_name      TEXT NOT NULL,
    last_name       TEXT NOT NULL,
    phone           TEXT NULL,
    address_line1   TEXT NULL,
    address_line2   TEXT NULL,
    city            TEXT NULL,
    state           TEXT NULL,
    postal_code     TEXT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================================================================
-- accounts
-- =========================================================================
CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id   UUID NOT NULL REFERENCES users(id),
    account_number  TEXT NOT NULL UNIQUE,
    account_type    TEXT NOT NULL CHECK (account_type IN ('CHECKING', 'SAVINGS')),
    balance_cents   BIGINT NOT NULL DEFAULT 0 CHECK (balance_cents >= 0),
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_owner_user_id ON accounts(owner_user_id);

-- =========================================================================
-- transfers
-- =========================================================================
CREATE TABLE transfers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key         UUID NOT NULL UNIQUE,
    source_account_id       UUID NOT NULL REFERENCES accounts(id),
    destination_account_id  UUID NOT NULL REFERENCES accounts(id),
    amount_cents            BIGINT NOT NULL CHECK (amount_cents > 0),
    currency                VARCHAR(3) NOT NULL DEFAULT 'USD',
    description             TEXT NULL,
    status                  TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    failure_reason          TEXT NULL,
    initiated_by_user_id    UUID NOT NULL REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at            TIMESTAMPTZ NULL,
    CONSTRAINT chk_transfer_accounts_differ CHECK (source_account_id <> destination_account_id)
);

CREATE INDEX idx_transfers_source_account_id ON transfers(source_account_id);
CREATE INDEX idx_transfers_destination_account_id ON transfers(destination_account_id);

-- =========================================================================
-- ledger_entries (double-entry bookkeeping)
-- =========================================================================
CREATE TABLE ledger_entries (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id            UUID NOT NULL REFERENCES transfers(id),
    account_id             UUID NOT NULL REFERENCES accounts(id),
    entry_type             TEXT NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount_cents           BIGINT NOT NULL CHECK (amount_cents > 0),
    balance_after_cents    BIGINT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_transfer_id ON ledger_entries(transfer_id);

-- =========================================================================
-- audit_events (append-only)
-- =========================================================================
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id   UUID NULL REFERENCES users(id),
    event_type      TEXT NOT NULL,
    entity_type     TEXT NULL,
    entity_id       UUID NULL,
    metadata        JSONB NULL,
    ip_address      TEXT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_events_actor_user_id ON audit_events(actor_user_id);
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type, entity_id);
CREATE INDEX idx_audit_events_created_at ON audit_events(created_at);

-- =========================================================================
-- idempotency_records
-- =========================================================================
CREATE TABLE idempotency_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key      UUID NOT NULL UNIQUE,
    user_id              UUID NOT NULL REFERENCES users(id),
    endpoint             TEXT NOT NULL,
    request_hash         TEXT NOT NULL,
    response_status      INT NULL,
    response_body        JSONB NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_records_expires_at ON idempotency_records(expires_at);
