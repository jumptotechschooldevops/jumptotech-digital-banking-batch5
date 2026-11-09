# JumpToTech Digital Banking

A realistic banking simulation (fictional customers, simulated funds only)
built as a modular monolith: Spring Boot + PostgreSQL backend, React +
TypeScript frontend. Built for a DevOps/SRE educational project — this repo
is the application; infrastructure (Docker, Kubernetes, CI/CD, monitoring)
is a later phase and intentionally not included yet.

No real financial transactions, payment gateways, or banking integrations.

## Status

Stage 1 (architecture) complete. See `docs/architecture.md`,
`docs/database.md`, `docs/api.md`, and `docs/local-development.md` for the
full design. Backend and frontend implementation are the next stages.

## Project structure

```
jumptotech-digital-banking/
├── backend/   Spring Boot 3.x (Java 21), Maven, Flyway, PostgreSQL
├── frontend/  React + TypeScript + Vite + Tailwind
├── docs/      architecture.md, database.md, api.md, local-development.md
├── .env.example
└── .gitignore
```

## Quick start

See `docs/local-development.md` for exact, step-by-step instructions
(database setup, environment variables, running migrations, starting both
servers, loading demo data, and testing a transfer end to end).

## Technology

- **Backend:** Java 21, Spring Boot 3.x, Spring Web, Spring Security, Spring
  Data JPA, Hibernate, Jakarta Bean Validation, Flyway, Maven
- **Frontend:** React, TypeScript, Vite, Tailwind CSS, React Router, TanStack
  Query, React Hook Form, Zod
- **Database:** PostgreSQL 16
- **Testing:** JUnit 5, Mockito, Spring Boot integration tests, Testcontainers
  (PostgreSQL), Vitest, React Testing Library, Playwright

## Core guarantees

- Money is always integer cents (`BIGINT`), never floating point.
- A transfer is atomic: both the debit and the credit happen, or neither does.
- Every transfer produces balanced double-entry ledger rows.
- Passwords are hashed with Argon2id; sessions use HttpOnly cookies with CSRF
  protection; no auth tokens are ever stored in `localStorage`.
- All financial data shown in the UI comes from the backend API — nothing is
  hardcoded in a React component.
