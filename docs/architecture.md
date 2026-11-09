# Architecture — JumpToTech Digital Banking

## 1. Purpose and scope

JumpToTech Digital Banking is a realistic, fully functional banking **simulation**
used as a target application for a DevOps/SRE educational platform. It moves
simulated funds between fictional customer accounts. It never touches real
money, real payment rails, or real banking infrastructure.

Version 1 is a **modular monolith**: one deployable Spring Boot application,
one PostgreSQL database, one React single-page app. Modules are separated by
package and by service *interface* so that a module (most likely `transfer`
or `ledger`) can be extracted into its own service later without rewriting
business logic — only the transport layer changes.

We deliberately defer distributed transactions. Correctness of a single
atomic transfer inside one database is the hard problem; a saga or
two-phase-commit across services is a harder problem we are not solving yet.

## 2. High-level component view

```
┌─────────────────────┐        HTTPS (dev: HTTP)        ┌──────────────────────────┐
│   React SPA          │ ───────────────────────────▶   │  Spring Boot application │
│   (Vite, port 5173)  │ ◀───────────────────────────    │  (port 8080)             │
│   - session cookie   │        JSON over REST            │  - Spring Security        │
│   - CSRF header       │                                  │  - Spring Data JPA       │
└──────────────────────┘                                  └─────────────┬────────────┘
                                                                          │ JDBC
                                                                          ▼
                                                            ┌──────────────────────────┐
                                                            │ PostgreSQL 16             │
                                                            │ (jumptotech_banking db)   │
                                                            └──────────────────────────┘
```

In local development, Vite's dev server proxies `/api/*` and `/actuator/*`
to `http://localhost:8080` so the browser only ever talks to one origin,
which keeps cookies first-party and avoids CORS complexity.

## 3. Module boundaries (backend)

Package root: `com.jumptotech.banking`

| Module        | Owns                                                                 | Exposes                         |
|---------------|-----------------------------------------------------------------------|----------------------------------|
| `auth`        | `User` entity, registration, login/logout, session, rate limiting    | `AuthService` interface          |
| `customer`    | `CustomerProfile` entity, profile read/update                        | `CustomerProfileService`         |
| `account`     | `Account` entity, balances, ownership checks                         | `AccountService`                 |
| `transfer`    | Orchestrates a transfer: validation, locking, idempotency, outcome   | `TransferService`                |
| `ledger`      | `LedgerEntry` entity, double-entry bookkeeping, invariant enforcement| `LedgerService` (internal to transfer) |
| `transaction` | Read-model over ledger + transfers for history/pagination/filtering  | `TransactionQueryService`        |
| `admin`       | Aggregate read-only views for administrators                         | `AdminSummaryService`            |
| `audit`       | `AuditEvent` entity, append-only audit trail                         | `AuditService`                   |
| `config`      | Security config, web config, Jackson/OpenAPI config, bean wiring     | —                                |
| `common`       | `Money` (integer-cents value type), `ApiError`, global exception handler, pagination DTOs, base entity | — |

**Rule enforced by package structure:** controllers depend only on the
service *interface* of their own module and on other modules' service
interfaces — never on another module's repository or entity. This is what
makes a future extraction (e.g. `transfer` + `ledger` → a `ledger-service`)
a matter of swapping an implementation behind `TransferService`/`LedgerService`
for an HTTP/gRPC client, not a rewrite.

`transfer` is intentionally the only module allowed to write to `ledger` and
to mutate `account.balance_cents`. No other module writes a balance.

## 4. Why session-based auth, not JWT-in-localStorage

The spec requires HttpOnly cookies and forbids tokens in `localStorage`
(the classic XSS exfiltration vector for bearer tokens). We use Spring
Security's server-side session:

- On login, Spring Security creates a session; the session ID is sent as an
  `HttpOnly`, `SameSite=Lax` cookie (`Secure` flag on in any HTTPS profile,
  off only for local plain-HTTP dev).
- CSRF protection is mandatory because we use cookies: we use Spring
  Security's `CookieCsrfTokenRepository` with a **non-HttpOnly** CSRF
  cookie (the token itself is not a secret credential — it only proves the
  request originated from a page that could read the cookie, i.e. same
  origin) and TanStack Query attaches it as the `X-XSRF-TOKEN` header.
- Logout invalidates the server-side session explicitly.
- Sessions are in-memory for v1 (single instance, local dev). This is noted
  in `database.md`/`local-development.md` as a scaling limitation to revisit
  (Spring Session + JDBC or Redis) when this becomes more than one instance —
  out of scope for this phase.

## 5. The transfer invariant (the most important subsystem)

A transfer is only ever "an all-or-nothing move of N cents from account A to
account B, recorded as two balanced ledger entries." The `TransferService`
enforces, in one `@Transactional` boundary:

1. **Ownership** — the authenticated user must own the source account.
   Checked against the database, never trusted from the request body.
2. **Eligibility** — destination account must exist, be `ACTIVE`, and not be
   the same account as the source.
3. **Amount validation** — `amountCents > 0`, bean-validated at the DTO
   layer and re-checked in the service layer (never trust the frontend).
4. **Idempotency** — the client sends an `Idempotency-Key` header. The
   service looks up `idempotency_records` first; if the key was already
   processed, it returns the stored result instead of re-executing the
   transfer. This covers retried requests (e.g. a user double-clicking
   "Confirm," or a client retry after a dropped response).
5. **Locking** — both accounts are locked with `SELECT ... FOR UPDATE`
   inside the same transaction, **always acquired in a fixed order (by
   account UUID, ascending)** regardless of which account is source and
   which is destination. This total ordering is what prevents deadlocks
   between two concurrent transfers that move money in opposite directions
   between the same pair of accounts.
6. **Sufficient funds** — re-read balance under the lock; reject if the
   source balance is less than the amount.
7. **Debit + credit** — both balance updates and both `ledger_entries` rows
   are written in the same transaction. A ledger entry for a debit and the
   matching credit always sum to zero.
8. **Commit or rollback** — any failure after this point throws, and Spring's
   `@Transactional` rolls back every write, including the idempotency
   record, so a failed attempt can be legitimately retried with the same key.
9. **Audit** — an `audit_events` row is written for the transfer outcome
   (success or rejection), independent of the ledger.
10. **Response** — a `TransferConfirmationDto` with the new balances is
    returned; the frontend never computes a balance itself.

This is covered by explicit tests in Stage 2/5: unit tests for each rejection
path, a Testcontainers integration test for the full happy path and for
rollback-on-failure, and a concurrency test that fires many simultaneous
transfers at the same account pair and asserts the final balances are exactly
what arithmetic predicts (no lost updates, no deadlock).

## 6. Error handling strategy

A single `@RestControllerAdvice` (`common.GlobalExceptionHandler`) converts
every exception into a consistent JSON error body:

```json
{
  "timestamp": "2026-11-09T18:04:12Z",
  "status": 422,
  "error": "INSUFFICIENT_FUNDS",
  "message": "The source account does not have enough available balance.",
  "path": "/api/v1/transfers"
}
```

Domain exceptions (`InsufficientFundsException`, `AccountNotOwnedException`,
`InvalidTransferAmountException`, `DuplicateTransferException`, etc.) are
mapped to specific HTTP statuses (422, 403, 400, 409). Nothing is swallowed;
unexpected exceptions map to a generic 500 with a logged correlation ID and
no stack trace leaked to the client.

## 7. Frontend structure

```
frontend/src/
  api/            # typed API client, one file per backend module, wraps fetch + CSRF header
  components/      # shared UI: Card, Table, Button, Dialog, Sidebar, MobileNav, LoadingSpinner
  features/
    auth/          # login, register, session context
    dashboard/
    accounts/
    transfers/
    transactions/
    profile/
    admin/
  hooks/           # TanStack Query hooks per resource
  routes/          # React Router route tree, protected-route wrapper
  schemas/         # Zod schemas shared by React Hook Form
```

All data (balances, transactions, profile, admin summary) is fetched through
TanStack Query hooks that call the real backend — nothing is hardcoded in a
component. Route guards redirect unauthenticated users to `/login` and
non-admin users away from `/admin`.

## 8. Path to future microservices (not built now)

The module boundaries above are chosen so that, later:

- `ledger` + `transfer` could become a `ledger-service` behind a gRPC/REST
  interface, since it already only talks to `account` through
  `AccountService`, never through `AccountRepository`.
- `audit` could become an event-consumer service once we introduce a
  message bus — it already only receives fire-and-forget calls, not
  synchronous reads from anyone.
- `admin` is a pure read-model and could be served by a separate reporting
  service backed by a read replica.

None of this is implemented now. It is listed here only to explain *why* the
package boundaries and service interfaces look the way they do.
