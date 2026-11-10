# Local development

## Status

Backend (Stage 2), frontend (Stage 3), and full-stack integration (Stage 4)
are all implemented and verified on this machine. Every section below,
including the manual transfer walkthrough and the Playwright end-to-end test,
has actually been run against the real dev PostgreSQL database with both
servers up. Stage 5 (the complete, final test-suite pass with everything
documented together) is still to come, though both suites already pass
individually as part of Stage 4's verification.

## Dependency security status

An `npm audit` pass found 15 frontend vulnerabilities (11 moderate, 2 high, 2
critical). Fixed without unnecessary breaking changes:

- `minimatch` (moderate, ReDoS) - forced to `^11.0.5` via a package.json
  `overrides` entry, with no change to `@typescript-eslint`'s version; the
  CJS/ESM compatibility was checked first since a minimatch major jump has
  dropped CJS support before.
- `react-router-dom` 6.x (moderate, open redirect in `<Link>`/`useNavigate` -
  a phishing-relevant bug for a banking app) - upgraded to `^7.18.5`. The only
  code change needed was removing the `future` flags from `<BrowserRouter>`/
  `<MemoryRouter>`, since v7 made that behavior the permanent default.
- `zod` (high, memory-exhaustion DoS) - upgraded to `^4.6.7` along with
  `@hookform/resolvers` to `^7.0.5` (the version that added zod v4 support).
  No schema code changes were needed.
- `esbuild`/`vite` (moderate/high, dev-server request handling) - upgraded
  `vite` to `^6.4.6` and `@vitejs/plugin-react` to `^5.2.0`.
- `tinypool`/`@vitest/mocker`/`vite-node` (critical/moderate, prototype
  pollution and path traversal in the test runner) - upgraded `vitest` to
  `^5.1.0`, which was a much smaller change than it first looked once `vite`
  was already at 6.x (vitest 5's peer range accepts it directly).

All 46 backend tests, all 13 frontend tests, the production build, and the
Playwright end-to-end test were re-run after these changes and still pass.
`npm audit` now reports **0 vulnerabilities**.

**Not fixed (backend, out of scope for this pass):** `trivy fs backend`
reports 53 CRITICAL/HIGH findings, all transitive dependencies of Spring Boot
3.3.4 (Tomcat, Spring Framework, Spring Security, Jackson, BouncyCastle,
the PostgreSQL driver). These predate the dependency-audit work above and
need their own upgrade-and-retest pass (likely Spring Boot 3.3.4 -> 3.5.x) -
tracked as remaining work, not silently ignored. `.github/workflows/ci.yml`
scans both stacks on every run: the frontend scan blocks the build, the
backend scan is `continue-on-error` (reports to the Security tab, doesn't
fail CI yet) until that upgrade happens.

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

## 3. Running Flyway migrations

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

## 4. Starting the backend

```bash
cd backend
./mvnw spring-boot:run
```

Serves on `http://localhost:8080`. `/actuator/health` should return `{"status":"UP"}`.

## 5. Starting the frontend

```bash
cd frontend
npm install
npm run dev
```

Serves on `http://localhost:5173`. Vite's dev server proxies `/api` and
`/actuator` to `http://localhost:8080` (configured in `vite.config.ts`), so
the browser only talks to one origin and session cookies stay first-party.

## 6. Loading demo data

A dev-only seed script (not a Flyway migration — see `database.md` for why)
creates:

- Sarah Johnson — checking $8,500.00, savings $15,200.00
- Michael Davis — checking $4,200.00, savings $10,000.00
- one administrator account, credentials from `ADMIN_SEED_EMAIL`/`ADMIN_SEED_PASSWORD`

It will refuse to run unless `SPRING_PROFILES_ACTIVE` is `local` or `dev`.

## 7. Logging in and testing a transfer

Verified on this machine (Stage 4), both via the running UI and via direct
API calls through the frontend's proxy:

1. Open `http://localhost:5173/login`.
2. Log in as Sarah Johnson (demo credentials from the seed step).
3. Dashboard shows both account balances and recent transactions.
4. Go to Transfer, choose checking → savings, enter an amount, confirm.
5. The receipt shows updated balances, and the transaction history page
   reflects both the debit and the credit - confirmed against the database
   directly (`ledger_entries` carries a balanced DEBIT/CREDIT pair, `accounts`
   shows the new balances).

## 8. Running the test suites

```bash
cd backend && ./mvnw test                 # unit + Testcontainers integration tests - 46/46 passing
cd frontend && npm run build               # tsc -b && vite build - verified clean
cd frontend && npm run test               # Vitest + React Testing Library - 13/13 passing
cd frontend && npm run test:e2e           # Playwright - 1/1 passing against the live stack
```

For the Playwright suite, start the backend with a known seed password so
the test can log in (never commit the value you choose):

```bash
SPRING_PROFILES_ACTIVE=local SEED_DEMO_DATA=true SEED_SARAH_PASSWORD='choose-a-local-password' ./mvnw spring-boot:run
# in another terminal:
npm run dev
# in a third terminal, after installing browsers once with `npx playwright install chromium`:
SEED_SARAH_PASSWORD='choose-a-local-password' npx playwright test
```
