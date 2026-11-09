# Database design — JumpToTech Digital Banking

PostgreSQL 16. All schema changes go through Flyway migrations in
`backend/src/main/resources/db/migration`. Hibernate's `ddl-auto` is set to
`validate` (never `update`/`create`) in every profile — the schema is only
ever created by Flyway.

All monetary values are stored as `BIGINT` cents (`amount_cents`,
`balance_cents`). No `NUMERIC`/`FLOAT` money anywhere, by rule. Currency is
a fixed `VARCHAR(3)` = `'USD'` for v1 but stored explicitly so a future
multi-currency version isn't a schema migration surprise.

All primary keys are `UUID` (`gen_random_uuid()`, from the `pgcrypto`
extension) so account/transfer/user identifiers are not guessable and don't
leak row-count information.

## Entity-relationship overview

```
users ──1:1── customer_profiles
users ──1:N── accounts
accounts ──1:N── transfers (as source)
accounts ──1:N── transfers (as destination)
transfers ──1:N── ledger_entries
accounts ──1:N── ledger_entries
users ──1:N── audit_events (nullable actor)
idempotency_records (standalone, keyed by idempotency_key)
```

## Tables

### `users`

| Column                | Type           | Notes                                      |
|-----------------------|----------------|---------------------------------------------|
| id                    | UUID PK        | `gen_random_uuid()`                         |
| email                 | TEXT UNIQUE    | app normalizes to lowercase before every write/read, so a plain unique column gives case-insensitive uniqueness without the citext extension |
| password_hash         | TEXT           | Argon2id hash, never logged                  |
| role                  | TEXT           | `CUSTOMER` or `ADMIN`, CHECK constraint      |
| status                | TEXT           | `ACTIVE`, `LOCKED`, `DISABLED`, CHECK        |
| failed_login_attempts | INT NOT NULL DEFAULT 0 | reset on successful login            |
| locked_until          | TIMESTAMPTZ NULL | rate-limiting lockout expiry               |
| created_at            | TIMESTAMPTZ NOT NULL DEFAULT now() |                             |
| updated_at            | TIMESTAMPTZ NOT NULL DEFAULT now() |                             |

Indexes: unique on `email`.

### `customer_profiles`

| Column       | Type     | Notes                              |
|--------------|----------|--------------------------------------|
| id           | UUID PK  |                                      |
| user_id      | UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE | 1:1 with users |
| first_name   | TEXT NOT NULL |                                 |
| last_name    | TEXT NOT NULL |                                 |
| phone        | TEXT NULL |                                     |
| address_line1| TEXT NULL |                                     |
| address_line2| TEXT NULL |                                     |
| city         | TEXT NULL |                                     |
| state        | TEXT NULL |                                     |
| postal_code  | TEXT NULL |                                     |
| created_at   | TIMESTAMPTZ NOT NULL DEFAULT now() |               |
| updated_at   | TIMESTAMPTZ NOT NULL DEFAULT now() |               |

### `accounts`

| Column            | Type           | Notes                                           |
|-------------------|----------------|---------------------------------------------------|
| id                | UUID PK        |                                                     |
| owner_user_id     | UUID NOT NULL REFERENCES users(id) | account owner                  |
| account_number    | TEXT UNIQUE NOT NULL | full internal number, e.g. 10 digits; **frontend only ever sees a masked form** (`****1234`), computed in the DTO layer, never stored masked |
| account_type      | TEXT NOT NULL  | `CHECKING` or `SAVINGS`, CHECK constraint          |
| balance_cents     | BIGINT NOT NULL DEFAULT 0 CHECK (balance_cents >= 0) | never negative |
| currency          | VARCHAR(3) NOT NULL DEFAULT 'USD' |                                    |
| status            | TEXT NOT NULL DEFAULT 'ACTIVE' | `ACTIVE`, `FROZEN`, `CLOSED`, CHECK |
| created_at        | TIMESTAMPTZ NOT NULL DEFAULT now() |                                |
| updated_at        | TIMESTAMPTZ NOT NULL DEFAULT now() |                                |

Indexes: `owner_user_id`, unique on `account_number`.

`balance_cents >= 0` is a defense-in-depth database constraint — the
application must never attempt a write that violates it, but the constraint
guarantees it even if application logic has a bug.

### `transfers`

| Column                 | Type          | Notes                                      |
|------------------------|---------------|----------------------------------------------|
| id                     | UUID PK       |                                                |
| idempotency_key        | UUID UNIQUE NOT NULL |                                         |
| source_account_id      | UUID NOT NULL REFERENCES accounts(id) |                       |
| destination_account_id | UUID NOT NULL REFERENCES accounts(id) |                       |
| amount_cents           | BIGINT NOT NULL CHECK (amount_cents > 0) |                      |
| currency               | VARCHAR(3) NOT NULL DEFAULT 'USD' |                                 |
| description            | TEXT NULL     |                                                |
| status                 | TEXT NOT NULL | `PENDING`, `COMPLETED`, `FAILED`, CHECK        |
| failure_reason         | TEXT NULL     | populated only when status = FAILED            |
| initiated_by_user_id   | UUID NOT NULL REFERENCES users(id) |                           |
| created_at             | TIMESTAMPTZ NOT NULL DEFAULT now() |                             |
| completed_at           | TIMESTAMPTZ NULL |                                              |

Indexes: `source_account_id`, `destination_account_id`, unique on
`idempotency_key`. CHECK `source_account_id <> destination_account_id`.

### `ledger_entries`

Double-entry bookkeeping. Every completed transfer produces **exactly two**
rows here: one `DEBIT` on the source account, one `CREDIT` on the
destination account, same `amount_cents`, same `transfer_id`.

| Column             | Type          | Notes                                  |
|---------------------|---------------|-------------------------------------------|
| id                  | UUID PK       |                                            |
| transfer_id         | UUID NOT NULL REFERENCES transfers(id) |                   |
| account_id          | UUID NOT NULL REFERENCES accounts(id) |                    |
| entry_type          | TEXT NOT NULL | `DEBIT` or `CREDIT`, CHECK                |
| amount_cents        | BIGINT NOT NULL CHECK (amount_cents > 0) |                |
| balance_after_cents | BIGINT NOT NULL | account balance immediately after this entry, for audit/history display without recomputation |
| created_at          | TIMESTAMPTZ NOT NULL DEFAULT now() |                      |

Indexes: `account_id` (for transaction history queries), `transfer_id`.

Invariant checked by tests (not enforceable in a portable CHECK constraint
without triggers, so enforced in `LedgerService` and verified by an
integration test): for any `transfer_id`, `SUM(amount_cents WHERE entry_type
= 'DEBIT') = SUM(amount_cents WHERE entry_type = 'CREDIT')`.

### `audit_events`

Append-only; nothing ever updates or deletes a row here.

| Column         | Type          | Notes                                         |
|-----------------|---------------|--------------------------------------------------|
| id              | UUID PK       |                                                   |
| actor_user_id   | UUID NULL REFERENCES users(id) | null for system/anonymous events |
| event_type      | TEXT NOT NULL | e.g. `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `TRANSFER_COMPLETED`, `TRANSFER_REJECTED`, `PROFILE_UPDATED` |
| entity_type     | TEXT NULL     | e.g. `ACCOUNT`, `TRANSFER`, `USER`                |
| entity_id       | UUID NULL     |                                                    |
| metadata        | JSONB NULL    | structured context; **never contains password/session data** |
| ip_address      | TEXT NULL     |                                                    |
| created_at      | TIMESTAMPTZ NOT NULL DEFAULT now() |                             |

Indexes: `actor_user_id`, `(entity_type, entity_id)`, `created_at`.

### `idempotency_records`

| Column           | Type          | Notes                                         |
|-------------------|---------------|--------------------------------------------------|
| id                | UUID PK       |                                                   |
| idempotency_key   | UUID UNIQUE NOT NULL |                                            |
| user_id           | UUID NOT NULL REFERENCES users(id) | scoped per user            |
| endpoint          | TEXT NOT NULL | e.g. `POST /api/v1/transfers`                    |
| request_hash      | TEXT NOT NULL | hash of the normalized request body, to detect a key reused with a *different* payload (rejected as a conflict) |
| response_status   | INT NULL      | populated once the original request completes     |
| response_body     | JSONB NULL    |                                                     |
| created_at        | TIMESTAMPTZ NOT NULL DEFAULT now() |                               |
| expires_at        | TIMESTAMPTZ NOT NULL | records older than this are eligible for cleanup |

Indexes: unique on `idempotency_key`, `expires_at` (for cleanup).

## Locking strategy for transfers

```sql
SELECT id, balance_cents FROM accounts
WHERE id IN (:sourceId, :destinationId)
ORDER BY id
FOR UPDATE;
```

Locking both rows in one statement, ordered by primary key, inside the
transfer's `@Transactional` method, is what gives us:

- **No lost updates** — two concurrent transfers touching the same account
  serialize on the row lock.
- **No deadlock** — because the lock order is always ascending by UUID
  regardless of transfer direction, two transfers between the same account
  pair in opposite directions always request locks in the same order.

## Migrations

- `V1__init_schema.sql` — extensions (`pgcrypto`, `citext`), all seven
  tables, constraints, indexes.
- `V2__seed_dev_data.sql` is **not** a Flyway migration — seed data is
  intentionally *not* run through Flyway (so it can never accidentally run
  in a production profile via `flyway migrate`). It is a separate,
  dev-only SQL script invoked manually; see `local-development.md`.

## Why no triggers for the double-entry invariant

A `CHECK` or trigger enforcing "ledger entries for a transfer sum to zero"
was considered, but entries are inserted as two separate statements within
one transaction, and Postgres row-level CHECK constraints can't see other
rows. A deferred constraint trigger could do it, but for v1 we rely on the
invariant being enforced in exactly one place (`LedgerService`, called only
by `TransferService`) and verified by integration tests, consistent with
"no module other than `transfer` writes to the ledger." This is revisited if
the ledger is ever extracted into its own service with multiple writers.
