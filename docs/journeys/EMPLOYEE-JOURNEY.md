# Employee Journey: Booking Travel, Filing Expenses, Viewing the Audit Trail

## Overview

**Persona:** Alice (`alice.employee`, role `employee`, tenant `tenant-a`) acting for herself. Acting for someone else is covered in [`DELEGATE-JOURNEY.md`](DELEGATE-JOURNEY.md).

**Covers:** login → dashboard → create a travel authorization (booking) → list bookings → create an expense with line items → submit it → view the booking audit trail.

**Shape of every call:** the browser calls Next.js on the same origin; Next.js rewrites `/api/bff/*` to the BFF and `/api/bookings|expenses|delegations|consents/*` to the gateway. Both validate the JWT (issuer and audience). The BFF calls services directly; each service builds a `SecurityContext` and asks OPA before reading or writing.

| Component | Port | Role here |
|---|---|---|
| Next.js | 3000 | UI; Auth.js session; rewrites to BFF and gateway |
| Employee BFF | 8085 | Dashboard aggregation; booking and expense proxy |
| API Gateway | 8000 | Not used in this journey (delegation management, approvals, admin lists) |
| travel-service | 8081 | Bookings and booking audit |
| expense-service | 8082 | Expenses, items, expense audit; budget check on submit |
| Keycloak | 8080 | Realm `corporate-travel`, public client `employee-portal` (PKCE) |
| OPA | 8181 | `/v1/data/corporate/travel/authorization/allow` |
| PostgreSQL | 5432 | `travel` and `expense` schemas |

## Prerequisites

- Stack running (`podman compose up -d`), seeded or empty.
- Alice exists with roles `employee` and `default-roles-corporate-travel`, password `password123`.
- No active delegation in Alice's BFF session.

---

## 1. Login

Authorization code + PKCE through Auth.js; details in [`AUTH-AND-SESSION.md`](../AUTH-AND-SESSION.md).

Result: an encrypted session cookie and `useSession()` data:

```json
{
  "accessToken": "<jwt>",
  "user": { "id": "<keycloak-uuid>", "name": "Alice Employee", "email": "alice@tenant-a.com",
            "roles": ["employee", "default-roles-corporate-travel", "offline_access", "uma_authorization"],
            "tenantId": "tenant-a" }
}
```

The access token lasts 300 s; Auth.js refreshes it 60 s before expiry.

## 2. Dashboard

`EmployeeDashboard` → `getDashboard()` → `GET /api/bff/dashboard`.

| Header | Set by |
|---|---|
| `Authorization: Bearer <access token>` | `bffClient` interceptor |
| `Cookie: JSESSIONID` (once one exists) | browser (`withCredentials`) |

The BFF (`ApiAggregationService`) calls travel-service `GET /api/bookings` then expense-service `GET /api/expenses` with Alice's token and returns `{ "bookings": [...], "expenses": [...] }`.

## 3. Create a Booking

UI: `/travel/book` → `createBooking()` → `POST /api/bff/bookings` → BFF → travel-service `POST /api/bookings`.

```json
{
  "destination": "Singapore", "startDate": "2026-11-10", "endDate": "2026-11-17",
  "businessPurpose": "Client summit", "budget": 150000, "budgetCurrency": "INR",
  "tenantId": "tenant-a", "userId": "alice@tenant-a.com", "status": "PENDING"
}
```

`tenantId` and `userId` are required by validation but overwritten from the token; `status` is kept as sent.

OPA input and rule:

```json
{ "user": { "user_id": "alice.employee", "tenant_id": "tenant-a", "roles": ["employee", "..."] },
  "action": "create_booking",
  "resource": { "type": "booking", "tenant_id": "tenant-a", "user_id": "alice.employee" },
  "delegation": { "active": false }, "consent": { "valid": false, "scopes": [] } }
```

```rego
allow if { input.action == "create_booking"; is_same_tenant; has_role("employee") }
```

DB: row in `travel.bookings` (`user_id` = `created_by` = `alice.employee`, `status` `PENDING`) and a `CREATE` row in `travel.booking_audit`. Response: the booking (BFF returns 200). The UI then opens `/travel/{id}`.

Booking statuses and audit details: [`TRAVEL-AUTHORIZATION.md`](../TRAVEL-AUTHORIZATION.md).

## 4. List Own Bookings

UI: `/travel` → `getBookings()` → `GET /api/bff/bookings` → travel-service `GET /api/bookings`.

OPA `view_booking` with `resource.user_id = alice.employee` → `is_resource_owner`. Query: `findByTenantIdAndUserId("tenant-a", "alice.employee")`.

## 5. Create an Expense (with items)

UI: `/expense/submit` → `createExpense()` → `POST /api/bff/expenses` → expense-service `POST /api/expenses`. Items are sent inline; the form offers "save draft" or "submit".

```json
{
  "title": "Singapore Trip - Nov 2026", "bookingId": "<booking-id>", "currency": "INR",
  "totalAmount": 45000,
  "items": [ { "category": "TRAVEL", "description": "Flight SIN", "date": "2026-11-10",
               "amount": 45000, "currency": "INR" } ]
}
```

- OPA `create_expense`: same tenant and `employee` role.
- Owner = `subjectId` (Alice), `createdBy` = Alice, status `DRAFT`, audit `CREATE`.
- `bookingId` is optional; without it there is no budget ceiling.
- `totalAmount` is stored as sent; the item endpoints (`/api/expenses/{id}/items`, not used by the UI) recalculate it.

## 6. Submit the Expense

UI: "submit" on `/expense/submit`, or the submit button on `/expense/{id}` → `submitExpense()` → `POST /api/bff/expenses/{id}/submit` → expense-service `POST /api/expenses/{id}/submit`.

Checks, in order:

1. Status must be `DRAFT` (else 400) and the expense must have items (else 409).
2. OPA `submit_expense`: same tenant, owner, `resource.status == "DRAFT"`.
3. Budget: if `bookingId` is set and `totalAmount > booking.budget` → 422 (`BudgetExceededException`); the UI shows the overage. Skipped if the booking cannot be fetched.

DB: `status = SUBMITTED`, `submission_date`, `updated_by`; audit `SUBMIT` `{ itemCount, totalAmount }`.

## 7. View the Booking Audit Trail

UI: `/travel/{id}` → `GET /api/bff/bookings/{id}/audit` (through the BFF so delegation mode applies) → travel-service `GET /api/bookings/{id}/audit`.

travel-service first runs `view_booking` on the booking, then returns rows newest first:

```json
[ { "bookingId": "<id>", "action": "CREATE", "actorId": "alice.employee", "subjectId": "alice.employee",
    "details": { "destination": "Singapore", "budget": "150000", "budgetCurrency": "INR", "status": "PENDING" },
    "tenantId": "tenant-a", "delegationId": null, "consentId": null, "timestamp": "…" } ]
```

---

## Expense States

```
DRAFT ──submit──► SUBMITTED ──approve──► APPROVED
                            └─reject───► REJECTED
```

- `DRAFT`: editable by the owner (update, delete, items).
- `SUBMITTED`: locked; a manager or admin approves or rejects (see [`MANAGER-JOURNEY.md`](MANAGER-JOURNEY.md)).
- `APPROVED`, `REJECTED`: final in practice. `PAID` exists in the enum but nothing sets it.

---

## Error Scenarios

| Scenario | Status | Notes |
|---|---|---|
| Token expired and refresh fails | — | `/session-expired`, then `/login` |
| BFF or gateway rejects the token (invalid, expired, wrong `aud`) | 401 | Axios interceptor calls `signOut` |
| OPA denies (e.g. no `employee` role) | 403 | `ProblemDetail`, e.g. `Not authorized to create bookings` |
| Submit an expense that is not `DRAFT` | 400 | `Can only submit expenses in DRAFT status` |
| Submit an expense without items | 409 | `Cannot submit expense without any items` |
| Expense total over the booking budget | 422 | `Expense total … exceeds approved travel budget …` |
| Booking or expense not in Alice's tenant | 404 | |
| Missing required booking field or `budget` < 0.01 | 400 | Bean validation |
| Service error behind the BFF | 502 | Downstream 5xx; an unreachable service gives 500 |

---

## curl Example

```bash
TOKEN=$(curl -s http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d grant_type=password -d client_id=employee-bff -d client_secret=bff-service-secret-change-in-production \
  -d username=alice.employee -d password=password123 | jq -r .access_token)
BFF=http://localhost:8085/api/bff
H=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")

# Dashboard
curl -s "${H[@]}" $BFF/dashboard | jq '{bookings: (.bookings|length), expenses: (.expenses|length)}'

# Booking
BOOKING_ID=$(curl -s "${H[@]}" -X POST $BFF/bookings -d '{
  "destination": "Singapore", "startDate": "2026-11-10", "endDate": "2026-11-17",
  "businessPurpose": "Client summit", "budget": 150000, "budgetCurrency": "INR",
  "tenantId": "x", "userId": "x", "status": "PENDING" }' | jq -r .id)

# Expense with one item, then submit
EXPENSE_ID=$(curl -s "${H[@]}" -X POST $BFF/expenses -d "{
  \"title\": \"Singapore Trip\", \"bookingId\": \"$BOOKING_ID\", \"currency\": \"INR\", \"totalAmount\": 45000,
  \"items\": [{ \"category\": \"TRAVEL\", \"description\": \"Flight SIN\", \"date\": \"2026-11-10\",
               \"amount\": 45000, \"currency\": \"INR\" }] }" | jq -r .id)
curl -s "${H[@]}" -X POST $BFF/expenses/$EXPENSE_ID/submit | jq '{status, submissionDate}'

# Booking audit
curl -s "${H[@]}" $BFF/bookings/$BOOKING_ID/audit | jq '[.[] | {action, actorId, subjectId}]'
```

Expected: booking `PENDING`, expense `SUBMITTED`, one `CREATE` audit row for the booking.
