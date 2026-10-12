# Manager Journey: Approvals and Audit

## Overview

**Persona:** Bob (`bob.manager`, roles `employee` + `manager`, tenant `tenant-a`). Managers approve or reject `SUBMITTED` expenses in their tenant. They also act as ordinary employees for their own bookings and expenses ([`EMPLOYEE-JOURNEY.md`](EMPLOYEE-JOURNEY.md)).

**Covers:** login → manager dashboard → approve or reject an expense → expense and booking audit trails → what managers cannot do yet.

**Routing:** approve and reject go through the API Gateway (`gatewayClient`). Dashboard bookings, expense detail and expense audit go through the BFF (`bffClient`). Both carry the same Bearer token.

| Component | Port | Role |
|---|---|---|
| Keycloak | 8080 | Token with `manager` in `realm_access.roles` |
| Next.js | 3000 | `ManagerDashboard`, `/expense/{id}` |
| API Gateway | 8000 | Routes `/api/expenses/**` → expense-service, `/api/bookings/**` → travel-service; `LoggingFilter`, `SecurityHeadersFilter` |
| Employee BFF | 8085 | Dashboard, expense detail and audit |
| expense-service | 8082 | Expense state machine, OPA, audit |
| travel-service | 8081 | Bookings and booking audit |
| OPA | 8181 | `authorization.rego` |

## Prerequisites

- Stack running; Bob exists with password `password123`.
- At least one expense in `tenant-a` is `SUBMITTED` (`scripts/seed-data.sh` creates and approves some for Alice and Carol).

---

## 1. Login

Same PKCE flow as every user ([`AUTH-AND-SESSION.md`](../AUTH-AND-SESSION.md)). Bob's `session.user.roles` contains `employee` and `manager`, so `/dashboard` renders `ManagerDashboard` and the sidebar shows "Pending Approvals". In each service `realm_access.roles` becomes `SecurityContext.roles` and then OPA `input.user.roles`.

## 2. Manager Dashboard

`ManagerDashboard` loads in parallel:

| Call | Route | Returns |
|---|---|---|
| `getDashboard()` | BFF `GET /api/bff/dashboard` | Bob's own bookings and expenses |
| `getExpenses()` | gateway `GET /api/expenses` | expenses **owned by the caller** |
| `getMyDelegations()` | gateway `GET /api/delegations/my-delegations` | delegations Bob granted |

"Pending approvals" is `getExpenses()` filtered to `SUBMITTED`. Because `GET /api/expenses` returns only the caller's own expenses (`findByTenantIdAndUserId(tenant, caller)`), this list does not contain team members' expenses today. The `/approvals` page is a "Coming soon" placeholder.

## 3. Approve an Expense

UI: Approve on the dashboard or on `/expense/{id}` (shown for `manager`/`admin` when status is `SUBMITTED`) → `approveExpense(id)`.

```
POST /api/expenses/{id}/approve       body optional: { "comments": "…" }  (the UI sends none)
  → Next.js rewrite → gateway :8000 → expense-service :8082
```

`ExpenseServiceImpl.approveExpense`:

1. `findByIdAndTenantId` — 404 outside Bob's tenant.
2. Status must be `SUBMITTED` — else 400 `Can only approve expenses in SUBMITTED status`.
3. OPA `approve_expense`:

```json
{ "user": { "user_id": "bob.manager", "tenant_id": "tenant-a", "roles": ["employee", "manager", "..."] },
  "action": "approve_expense",
  "resource": { "type": "expense", "id": "<id>", "tenant_id": "tenant-a", "user_id": "alice.employee", "status": "SUBMITTED" },
  "delegation": { "active": false }, "consent": { "valid": false, "scopes": [] } }
```

```rego
allow if {
    input.action in ["approve_expense", "reject_expense"]
    is_same_tenant
    has_role("manager")
    input.resource.status == "SUBMITTED"
}
```

4. `status = APPROVED`, `approval_date = now`, `approver_id = bob.manager`; audit `APPROVE` `{ comments }`.

Response: 200 with the updated expense.

The rule checks only role, tenant and status: any manager may approve any submitted expense in the tenant, including their own.

## 4. Reject an Expense

`rejectExpense(id)` → `POST /api/expenses/{id}/reject`. Same steps as approve; the service calls OPA with action `approve_expense` for both. Result: `REJECTED`, `approver_id` and `approval_date` set, audit `REJECT` `{ comments }`.

## 5. Audit Trails

| Trail | UI call | Route |
|---|---|---|
| Expense | `getExpenseAudit(id)` on `/expense/{id}` | BFF `GET /api/bff/expenses/{id}/audit` → expense-service `/api/expenses/{id}/audit` |
| Booking | booking detail page | BFF `GET /api/bff/bookings/{id}/audit` |
| Booking (admin pages) | `getBookingAudit(id)` | gateway `GET /api/bookings/{id}/audit` |

Both services first authorize `view_*` on the parent record, then return rows newest first. Expense audit actions: `CREATE`, `UPDATE`, `DELETE`, `SUBMIT`, `APPROVE`, `REJECT` (item changes are not audited).

```json
[
  { "action": "APPROVE", "actorId": "bob.manager",    "subjectId": "bob.manager",    "details": { "comments": "Approved" } },
  { "action": "SUBMIT",  "actorId": "alice.employee", "subjectId": "alice.employee", "details": { "itemCount": 1, "totalAmount": "45000" } },
  { "action": "CREATE",  "actorId": "alice.employee", "subjectId": "alice.employee", "details": { "status": "DRAFT", "title": "…" } }
]
```

`view_expense` allows the owner, an active delegate, `is_manager_of_user` or an admin. `resource.manager_chain` is never populated, so Bob gets 403 on another employee's expense detail and audit, even though he can approve that expense.

## 6. Not Available to Managers

| Wanted | Today |
|---|---|
| See team bookings or expenses | No endpoint; `/api/bookings` and `/api/expenses` return the caller's own records; `/all` variants are `admin`-only |
| Confirm a booking (`PUT /api/bookings/{id}/status`) | 403: `update_booking` allows the owner, a delegate with `book_travel`, or an admin |
| Approval inbox page | `/approvals` is a placeholder |

---

## Error Scenarios

| Scenario | Status | Message |
|---|---|---|
| Approve or reject an expense that is not `SUBMITTED` | 400 | `Can only approve expenses in SUBMITTED status` / `…reject…` |
| Expense in another tenant, or unknown id | 404 | `Expense not found …` |
| Caller lacks `manager` (and `admin`) | 403 | `Not authorized to approve this expense` |
| Token missing, expired, or wrong `aud` | 401 | Gateway rejects; Axios interceptor signs out |
| View another employee's expense detail | 403 | `view_expense` denied (see above) |

---

## curl Example

```bash
token() { curl -s http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d grant_type=password -d client_id=employee-bff -d client_secret=bff-service-secret-change-in-production \
  -d username="$1" -d password=password123 | jq -r .access_token; }
ALICE=$(token alice.employee); BOB=$(token bob.manager)

# Alice creates and submits an expense
EXPENSE_ID=$(curl -s -X POST http://localhost:8000/api/expenses \
  -H "Authorization: Bearer $ALICE" -H "Content-Type: application/json" \
  -d '{ "title": "Taxi", "currency": "INR", "totalAmount": 900,
        "items": [{ "category": "TRANSPORTATION", "description": "Airport", "date": "2026-11-10", "amount": 900, "currency": "INR" }] }' | jq -r .id)
curl -s -X POST http://localhost:8000/api/expenses/$EXPENSE_ID/submit -H "Authorization: Bearer $ALICE" | jq .status

# Bob approves (or: .../reject)
curl -s -X POST http://localhost:8000/api/expenses/$EXPENSE_ID/approve \
  -H "Authorization: Bearer $BOB" -H "Content-Type: application/json" \
  -d '{ "comments": "Receipts verified" }' | jq '{status, approverId, approvalDate}'

# Audit, read as the owner
curl -s http://localhost:8000/api/expenses/$EXPENSE_ID/audit \
  -H "Authorization: Bearer $ALICE" | jq '[.[] | {action, actorId}]'
```

The tenant always comes from the token's `tenant_id`; no tenant header is needed.
