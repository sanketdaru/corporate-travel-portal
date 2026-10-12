# Travel Authorization Model

**For:** developers working on bookings, status changes, budget enforcement or the booking audit trail.

**Covers:** a booking's path from the UI through the BFF into travel-service, OPA and PostgreSQL; what each write audits; how the budget is enforced.

| Component | Role |
|---|---|
| Next.js (`/travel/book`, `lib/api/bff.ts`) | Creates bookings through `POST /api/bff/bookings` |
| employee-bff (`BookingBffController`, `TravelServiceClient`) | Picks the user's token or, in delegation mode, the travel-service delegated token plus delegation headers |
| travel-service (`BookingController`, `BookingServiceImpl`) | Validates input, asks OPA, writes the booking and its audit row |
| OPA (`authorization.rego`) | Tenant, role, ownership and delegation checks |
| PostgreSQL `travel.bookings`, `travel.booking_audit` | Bookings and their immutable history |

## Prerequisites

- Stack running (`podman compose up -d`); travel-service Flyway migrations V1–V4 applied.
- Tokens carry `preferred_username`, `tenant_id`, `realm_access.roles` and `aud` containing `travel-service` (see `AUTH-AND-SESSION.md`).

---

## The Model: a Booking Is a Travel Authorization

A booking is a request to undertake a business trip with a pre-approved budget, not a purchase. Actual costs are recorded later as expense line items.

- V3 dropped `booking_type` and `total_amount`; the ceiling is `budget` (`DECIMAL(12,2)`, > 0) plus `budget_currency` (ISO 4217, default `INR`).
- V4 added the optional `business_purpose` and `notes`.
- Expenses may reference a booking via `bookingId`; the budget is enforced when such an expense is submitted.

---

## Create Booking

```
POST /api/bff/bookings     browser → BFF (via Next.js rewrite)
POST /api/bookings         BFF → travel-service :8081
```

**Body.** `destination`, `startDate`, `endDate`, `budget` are required; `budgetCurrency`, `businessPurpose`, `notes`, `details` (JSON) are optional. Bean validation (`@Valid` on the entity) also requires `tenantId`, `userId` and `status`; the UI sends placeholders for the first two and `status: "PENDING"`.

**Headers BFF → travel-service**

| Header | Direct | Delegation mode |
|---|---|---|
| `Authorization` | user's own token | delegated token (`sub`=delegator, `act.sub`=actor, `aud`=`travel-service`) |
| `X-Actor-Token` | — | actor's own token |
| `X-Delegation-Id`, `X-Consent-Id`, `X-Delegation-Purpose` | — | audit metadata |

**Service logic (`BookingServiceImpl.createBooking`)**

1. `tenantId` ← context; `userId` ← `subjectId` (the delegator in delegation mode, else the caller). Body values are overwritten.
2. `createdBy` and `updatedBy` ← the actor.
3. `status` is kept from the body (the `PENDING` default only applies if it were null).
4. OPA `create_booking`: same tenant and `employee` role; or an active delegation whose purpose grants `book_travel`.
5. Insert, then audit `CREATE` with `destination`, `budget`, `budgetCurrency`, `status`, in the same transaction.

**Response.** travel-service returns 201 with the `Booking`; the BFF relays the body with 200.

---

## Update Booking Status

```
PUT /api/bookings/{id}/status     body: { "status": "CONFIRMED" }
```

No UI calls this yet; use the gateway or the service directly.

1. `findByIdAndTenantId` — 404 outside the caller's tenant.
2. OPA `update_booking` with `current_status` and `new_status`: the owner; a delegate with `book_travel`; or an `admin`. Managers have no rule for this.
3. Set status and `updatedBy`, save, audit `STATUS_CHANGE` `{ oldStatus, newStatus }`.

Response: 200 with the updated booking.

## Get Booking Budget (internal)

```
GET /api/bookings/{id}/budget?tenantId=…  →  { "id": "…", "budget": 120000.00, "budgetCurrency": "INR" }
```

Called by expense-service over the compose network with its service-account token (client credentials, ADR-009). The shared `SecurityConfig` requires realm role `budget_reader`, which only `service-account-expense-service` holds; user tokens get 403, no token 401. The token carries no tenant, so the caller passes the expense's tenant; a booking in another tenant is 404, a missing `tenantId` 400. No OPA check. The gateway denies this path, so only the compose network reaches it.

## Delete Booking

```
DELETE /api/bookings/{id}   → 204
```

OPA action `delete_booking` has no dedicated rule, so only the `admin` rule allows it. The service deletes the row, then audits `DELETE` `{ status }`.

Known issue: `booking_audit.booking_id` is a foreign key to `bookings.id` without cascade, and every booking has a `CREATE` audit row, so the delete is expected to fail with a constraint violation.

---

## Status Values

`BookingStatus` (`services/shared/domain-models`): `DRAFT`, `PENDING`, `SUBMITTED`, `CONFIRMED`, `CANCELLED`.

There is no transition logic in code: any authorized caller can set any value. The UI creates bookings as `PENDING`; the usual path is `PENDING → CONFIRMED → CANCELLED`. There is no `COMPLETED` or `APPROVED` value; adding one means changing the enum and adding a Flyway migration.

---

## Budget Enforcement

Enforced only on expense submission (`ExpenseServiceImpl.submitExpense`):

1. If the expense has a `bookingId`, expense-service calls `GET /api/bookings/{id}/budget?tenantId=<expense tenant>`.
2. If **this expense's** `totalAmount` exceeds `budget` → `BudgetExceededException` → 422.
3. If the booking does not exist in the expense's tenant (404) → `LinkedBookingNotFoundException` → 422; the expense stays `DRAFT`.
4. Any other failure (travel-service unreachable, token rejected, bad response) → `TravelServiceUnavailableException` → 503; the expense stays `DRAFT`.

Not checked: booking status, other expenses against the same booking, or currency mismatch. On create, `totalAmount` is taken from the request body (the UI sends the sum of its items); only the item endpoints recalculate it. Booking create and status updates only validate `budget >= 0.01`.

---

## Audit Trail

`BookingAuditService.record(...)` writes one `travel.booking_audit` row per write, in the booking's transaction.

| Column | Value |
|---|---|
| `booking_id` | the booking |
| `actor_id` | who acted (`context.actorId`) |
| `subject_id` | on whose behalf (`context.subjectId`) |
| `action` | `CREATE`, `STATUS_CHANGE`, `DELETE` |
| `details` (JSONB) | `CREATE`: destination, budget, budgetCurrency, status · `STATUS_CHANGE`: oldStatus, newStatus · `DELETE`: status |
| `tenant_id` | tenant |
| `delegation_id`, `consent_id` | UUIDs in delegation mode, else null |
| `timestamp` | set on persist |

Direct requests have `actor_id == subject_id`. In delegation mode: `actor_id` = `dave.assistant`, `subject_id` = `carol.executive`, plus the delegation and consent ids.

Read it with `GET /api/bff/bookings/{id}/audit` (booking detail page; delegation-aware) or `GET /api/bookings/{id}/audit` (gateway; admin pages). Both first check `view_booking` on the booking. Newest first.

---

## Error Scenarios

| Scenario | Status | Response |
|---|---|---|
| OPA denies create (wrong tenant, no `employee` role, no matching delegation) | 403 | `Not authorized to create bookings` |
| OPA denies status update (not owner, delegate, or admin) | 403 | `Not authorized to update this booking` |
| Booking not in caller's tenant | 404 | `BookingNotFoundException` |
| `status` missing in status update | 400 | `Status is required` |
| Unknown status value | 400 | `IllegalArgumentException` |
| Missing required field, or `budget` < 0.01 | 400 | Bean validation |
| `GET /api/bookings/all` without `admin` | 403 | `Only admin role can list all tenant bookings` |
| Via BFF: downstream 4xx / 5xx | same 4xx / 502 | BFF error mapping |

---

## curl Example

```bash
TOKEN=$(curl -s http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d grant_type=password -d client_id=employee-bff -d client_secret=bff-service-secret-change-in-production \
  -d username=alice.employee -d password=password123 | jq -r .access_token)

# 1. Create (through the BFF, as the UI does)
BOOKING_ID=$(curl -s -X POST http://localhost:8085/api/bff/bookings \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{ "destination": "London, UK", "startDate": "2026-11-10", "endDate": "2026-11-17",
        "businessPurpose": "Q4 client review", "budget": 120000, "budgetCurrency": "INR",
        "tenantId": "x", "userId": "x", "status": "PENDING" }' | jq -r .id)

# 2. Confirm (owner, through the gateway)
curl -s -X PUT http://localhost:8000/api/bookings/$BOOKING_ID/status \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{ "status": "CONFIRMED" }' | jq .status

# 3. Audit trail: STATUS_CHANGE, then CREATE
curl -s http://localhost:8000/api/bookings/$BOOKING_ID/audit \
  -H "Authorization: Bearer $TOKEN" | jq '[.[] | {action, actorId, subjectId}]'
```

Creating a booking for someone else needs an activated delegation in the BFF session; see `journeys/DELEGATE-JOURNEY.md`. The result has `userId` = Carol, `createdBy` = Dave, and an audit row with `delegationId` and `consentId`.

---

## See Also

- `docs/journeys/DELEGATE-JOURNEY.md` — delegation grant, activation and delegated calls
- `docs/AUTHORIZATION-GUIDE.md` — `SecurityContext`, OPA input and rules
- `docs/AUTH-AND-SESSION.md` — login, tokens, BFF session
- `infrastructure/opa/policies/authorization.rego` — full policy
