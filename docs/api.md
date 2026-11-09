# API contracts — JumpToTech Digital Banking

All endpoints are versioned under `/api/v1`. All request/response bodies are
JSON. Authentication is a server-side session identified by an `HttpOnly`
cookie (`JSESSIONID` by default); mutating requests (`POST`/`PATCH`) must
include the CSRF header `X-XSRF-TOKEN`, whose value comes from the
non-HttpOnly `XSRF-TOKEN` cookie set by the backend.

DTOs are hand-written — entities are never serialized directly.

## Error shape

Every non-2xx response has this body:

```json
{
  "timestamp": "2026-11-09T18:04:12Z",
  "status": 422,
  "error": "INSUFFICIENT_FUNDS",
  "message": "The source account does not have enough available balance.",
  "path": "/api/v1/transfers"
}
```

`error` is a stable machine-readable code the frontend can switch on;
`message` is safe to show the user.

---

## Auth

### `POST /api/v1/auth/register`

```json
// request
{ "email": "sarah.johnson@example.com", "password": "•••••••••", "firstName": "Sarah", "lastName": "Johnson", "phone": "555-010-1234" }
```

- 201 → `{ "id": "uuid", "email": "...", "firstName": "...", "lastName": "..." }`
- 409 `EMAIL_ALREADY_REGISTERED` if the email is taken
- 400 `VALIDATION_ERROR` with field errors if password policy / email format fails

Automatically opens a `CHECKING` and `SAVINGS` account at $0.00 for the new
customer (so the product is usable immediately; demo balances come only
from seed data, never from registration).

### `POST /api/v1/auth/login`

```json
{ "email": "sarah.johnson@example.com", "password": "•••••••••" }
```

- 200 → `{ "id": "uuid", "email": "...", "role": "CUSTOMER", "firstName": "...", "lastName": "..." }`, sets session cookie
- 401 `INVALID_CREDENTIALS` — **generic message** regardless of whether the
  email exists or the password is wrong (prevents account enumeration)
- 423 `ACCOUNT_LOCKED` after too many failed attempts (rate limiting)

### `POST /api/v1/auth/logout`

- 204, invalidates the server-side session and clears the session cookie

### `GET /api/v1/auth/me`

- 200 → `{ "id": "uuid", "email": "...", "role": "CUSTOMER", "firstName": "...", "lastName": "..." }`
- 401 if not authenticated

---

## Accounts

### `GET /api/v1/accounts`

- 200 → list of the authenticated user's own accounts only:
```json
[
  { "id": "uuid", "maskedAccountNumber": "****4821", "accountType": "CHECKING", "balanceCents": 850000, "currency": "USD", "status": "ACTIVE" },
  { "id": "uuid", "maskedAccountNumber": "****7793", "accountType": "SAVINGS",  "balanceCents": 1520000, "currency": "USD", "status": "ACTIVE" }
]
```

### `GET /api/v1/accounts/{accountId}`

- 200 → same shape as above, single object
- 403 `ACCOUNT_NOT_OWNED` if the account exists but belongs to someone else
  (never 404 — a 404 here would let a client distinguish "doesn't exist"
  from "not yours" and enumerate account IDs)
- 404 if the account truly doesn't exist

### `GET /api/v1/accounts/{accountId}/transactions`

Query params: `page` (0-based, default 0), `size` (default 20, max 100),
`type` (`DEBIT`|`CREDIT`, optional), `from`/`to` (ISO date, optional).

- 200 →
```json
{
  "content": [
    {
      "id": "uuid",
      "transferId": "uuid",
      "occurredAt": "2026-11-08T14:22:00Z",
      "type": "DEBIT",
      "amountCents": 25000,
      "balanceAfterCents": 825000,
      "status": "COMPLETED",
      "counterpartyMaskedAccountNumber": "****7793",
      "description": "Transfer to savings"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```
- 403 `ACCOUNT_NOT_OWNED` for someone else's account

---

## Transfers

### `POST /api/v1/transfers`

Header: `Idempotency-Key: <uuid>` (required).

```json
{ "sourceAccountId": "uuid", "destinationAccountId": "uuid", "amountCents": 25000, "description": "Transfer to savings" }
```

- 201 → `TransferConfirmationDto`:
```json
{
  "transferId": "uuid",
  "status": "COMPLETED",
  "amountCents": 25000,
  "sourceAccount": { "id": "uuid", "maskedAccountNumber": "****4821", "balanceCents": 825000 },
  "destinationAccount": { "id": "uuid", "maskedAccountNumber": "****7793", "balanceCents": 1545000 },
  "completedAt": "2026-11-09T18:04:12Z"
}
```
- 200 (not 201) if the same `Idempotency-Key` was already processed —
  returns the original stored result, does not re-execute the transfer
- 400 `INVALID_TRANSFER_AMOUNT` if `amountCents <= 0`
- 403 `ACCOUNT_NOT_OWNED` if the authenticated user doesn't own the source account
- 404 `DESTINATION_ACCOUNT_NOT_FOUND` / 422 `DESTINATION_ACCOUNT_INELIGIBLE` (closed/frozen, or same as source)
- 422 `INSUFFICIENT_FUNDS`
- 409 `IDEMPOTENCY_KEY_CONFLICT` if the same key is reused with a **different** request body

### `GET /api/v1/transfers/{transferId}`

- 200 → full `TransferDetailDto` (only if the requester owns the source or
  destination account)
- 403 if neither account belongs to the requester

---

## Customers

### `GET /api/v1/customers/me`

- 200 → `{ "firstName", "lastName", "email", "phone", "addressLine1", "addressLine2", "city", "state", "postalCode" }`

### `PATCH /api/v1/customers/me`

Only `phone`, `addressLine1`, `addressLine2`, `city`, `state`, `postalCode`
are editable. `email`, `firstName`, `lastName` are **not** patchable through
this endpoint in v1 (changing legal name/email is out of scope — would
need re-verification flows).

- 200 → updated profile
- 400 `VALIDATION_ERROR` on bad input

---

## Admin

### `GET /api/v1/admin/summary`

Requires role `ADMIN`.

- 200 →
```json
{
  "totalCustomers": 2,
  "totalAccounts": 4,
  "totalBalanceCents": 3790000,
  "recentTransfers": [
    { "transferId": "uuid", "amountCents": 25000, "status": "COMPLETED", "completedAt": "2026-11-09T18:04:12Z" }
  ]
}
```
- 403 `FORBIDDEN` for a `CUSTOMER` role (not 404 — but no customer data is
  ever included in the response regardless)

---

## Health

### `GET /actuator/health`

- 200 → Spring Boot Actuator default health payload. Only `health` and
  `info` actuator endpoints are exposed; everything else
  (`env`, `heapdump`, `beans`, ...) is disabled outside local dev profile.

---

## HTTP status code conventions used throughout

| Status | Meaning in this API                                      |
|--------|-------------------------------------------------------------|
| 200    | success (read, or idempotent replay of a prior write)        |
| 201    | resource created / transfer executed for the first time      |
| 204    | success, no body (logout)                                     |
| 400    | request validation failure                                     |
| 401    | not authenticated                                               |
| 403    | authenticated but not authorized for this resource              |
| 404    | resource genuinely does not exist                               |
| 409    | conflict (duplicate email, idempotency key reused with different payload) |
| 422    | semantically invalid business operation (insufficient funds, ineligible destination) |
| 423    | account locked (rate limiting)                                   |
| 500    | unexpected server error (logged, generic message to client)      |
