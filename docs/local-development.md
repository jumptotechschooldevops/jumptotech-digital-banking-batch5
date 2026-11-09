# Local development

## Status

This document is accurate as of Stage 1 (architecture only — no backend or
frontend code exists yet). The database setup steps below have already been
run on this machine and verified. The "Running the backend" / "Running the
frontend" / "Loading demo data" sections describe what Stage 2/3 will add and
will be updated once that code exists — do not expect those steps to work
yet.

## Prerequisites (verified on this machine)

| Tool        | Required | Found                                           |
|-------------|----------|---------------------------------------------------|
| Java        | 21+      | OpenJDK 25.0.1 (Temurin) — Maven compiles with `--release 21` |
| Maven       | 3.9+     | 3.9.11                                            |
| Node.js     | 20+      | v22.23.2                                          |
| npm         | 10+      | 10.9.8                                            |
| PostgreSQL  | 16       | Installed via `brew install postgresql@16`, running as a brew service |

## 1. PostgreSQL setup (already done)

```bash
brew install postgresql@16
brew services start postgresql@16

export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"   # add to your shell profile

psql postgres -c "CREATE ROLE jumptotech_dev LOGIN PASSWORD 'jumptotech_dev_local_only';"
createdb -O jumptotech_dev jumptotech_banking
```

This created:
- role `jumptotech_dev` (local-dev-only password, not used anywhere else)
- database `jumptotech_banking`, owned by `jumptotech_dev`

To connect manually and inspect data:

```bash
psql "postgresql://jumptotech_dev:jumptotech_dev_local_only@localhost:5432/jumptotech_banking"
```

Useful queries once the schema exists (Stage 2):

```sql
\dt                                  -- list tables
SELECT * FROM accounts;
SELECT * FROM transfers ORDER BY created_at DESC LIMIT 10;
SELECT * FROM ledger_entries WHERE transfer_id = '<uuid>';
```

## 2. Environment variables

Copy `.env.example` to `.env` at the project root and fill in local values.
`.env` is git-ignored and never committed. Nothing in `.env.example` is a
real secret — it documents variable *names*, not values.

Key variables (finalized in Stage 2):
- `DB_URL=jdbc:postgresql://localhost:5432/jumptotech_banking`
- `DB_USERNAME=jumptotech_dev`
- `DB_PASSWORD=jumptotech_dev_local_only`
- `APP_SESSION_COOKIE_SECURE=false` (local HTTP dev only — must be `true` behind HTTPS)
- `SEED_DEMO_DATA=true` (local only — the seed script refuses to run if `SPRING_PROFILES_ACTIVE=prod`)
- `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD` — set these yourself locally; never committed, never defaulted to a known value in code

## 3. Running Flyway migrations *(Stage 2)*

Once the backend module exists:

```bash
cd backend
./mvnw -q flyway:migrate
```

Flyway runs automatically on application startup too (`spring.flyway.enabled=true`),
so this manual step is mainly for inspecting migration state ahead of time:

```bash
./mvnw -q flyway:info
```

## 4. Starting the backend *(Stage 2)*

```bash
cd backend
./mvnw spring-boot:run
```

Serves on `http://localhost:8080`. `/actuator/health` should return `{"status":"UP"}`.

## 5. Starting the frontend *(Stage 3)*

```bash
cd frontend
npm install
npm run dev
```

Serves on `http://localhost:5173`. Vite's dev server proxies `/api` and
`/actuator` to `http://localhost:8080` (configured in `vite.config.ts`), so
the browser only talks to one origin and session cookies stay first-party.

## 6. Loading demo data *(Stage 2)*

A dev-only seed script (not a Flyway migration — see `database.md` for why)
creates:

- Sarah Johnson — checking $8,500.00, savings $15,200.00
- Michael Davis — checking $4,200.00, savings $10,000.00
- one administrator account, credentials from `ADMIN_SEED_EMAIL`/`ADMIN_SEED_PASSWORD`

It will refuse to run unless `SPRING_PROFILES_ACTIVE` is `local` or `dev`.

## 7. Logging in and testing a transfer *(Stage 3/4, manual smoke test)*

1. Open `http://localhost:5173/login`.
2. Log in as Sarah Johnson (demo credentials from the seed step).
3. Dashboard should show both account balances and recent transactions.
4. Go to Transfer, choose checking → savings, enter an amount, confirm.
5. Confirm the receipt shows updated balances, and the transaction history
   page reflects both the debit and the credit.

## 8. Running the test suites *(Stage 5)*

```bash
cd backend && ./mvnw test                 # unit + Testcontainers integration tests
cd frontend && npm run test               # Vitest + React Testing Library
cd frontend && npm run test:e2e           # Playwright, requires both servers running
```
