# OPA-Based Authorization Guide

**For:** developers who know Spring Security but not Open Policy Agent.

**Covers:** how a request becomes a `SecurityContext`, what `OpaClient` sends to OPA, how the policy decides, and what a denial looks like — for direct and delegated requests.

**Why OPA:** rules combine roles, tenant isolation, ownership, delegation and consent. Keeping them in one Rego file keeps the services thin and makes policy reviewable without redeploying Java code.

| Component | Role |
|---|---|
| Keycloak | Issues JWTs with `preferred_username`, `tenant_id`, `realm_access.roles`; delegated tokens also carry `act` |
| `JwtAuthenticationConverter` | Builds the `SecurityContext` from the token (and, for delegated requests, the verified actor token) |
| `DelegatedActorFilter` | For tokens with `act`: verifies `X-Actor-Token` before the controller runs |
| `OpaClient` | `POST {opa.url}/v1/data/corporate/travel/authorization/allow`; fails closed |
| OPA (`:8181`) | Evaluates `infrastructure/opa/policies/authorization.rego` (Rego v1) |
| Service `GlobalExceptionHandler` | `AccessDeniedException` → 403 RFC 7807 `ProblemDetail` |

All of these except OPA live in `services/shared/security-commons` (the services' shared `SecurityConfig` registers the filter).

---

## Direct Request: Alice Creates a Booking

**1. Controller.** `BookingController.createBooking` (`POST /api/bookings`) calls `JwtAuthenticationConverter.extractSecurityContext(jwt, request)`. The token has no `act` claim, so the request is not delegated, whatever headers it carries.

| JWT claim | `SecurityContext` field |
|---|---|
| `preferred_username` (fallback `sub`) | `userId`, `username`; also `actorId` and `subjectId` |
| `tenant_id` | `tenantId` |
| `realm_access.roles` | `roles` |
| `employee_id`, `email` | `attributes` |

**2. Service.** `BookingServiceImpl.createBooking` sets owner and tenant from the context (never from the body), then calls `opaClient.authorize(context, "create_booking", resource)`:

```json
{
  "input": {
    "user":       { "user_id": "alice.employee", "tenant_id": "tenant-a", "roles": ["employee", "default-roles-corporate-travel", "..."] },
    "action":     "create_booking",
    "resource":   { "type": "booking", "tenant_id": "tenant-a", "user_id": "alice.employee" },
    "delegation": { "active": false },
    "consent":    { "valid": false, "scopes": [] }
  }
}
```

**3. Policy.**

```rego
allow if {
    input.action == "create_booking"
    is_same_tenant          # user.tenant_id == resource.tenant_id
    has_role("employee")
}
```

OPA returns `{"result": true}`.

**4. Deny.** `{"result": false}`, an unexpected response or an OPA error all make `authorize` return `false`; the service throws `AccessDeniedException("Not authorized to create bookings")` → 403.

---

## Delegated Request: Dave Creates a Booking for Carol

Delegation uses Keycloak RFC 8693 delegated tokens (ADR-024). The BFF obtains them on activation; see [`journeys/DELEGATE-JOURNEY.md`](journeys/DELEGATE-JOURNEY.md).

**1. Headers from the BFF to travel-service**

| Header | Value |
|---|---|
| `Authorization` | Delegated token: `sub` = Carol, `act.sub` = Dave's Keycloak UUID, `aud` = `travel-service` |
| `X-Actor-Token` | Dave's own access token |
| `X-Delegation-Id` | Delegation UUID (audit) |
| `X-Consent-Id` | Consent UUID from consent-service validation |
| `X-Delegation-Purpose` | e.g. `book_travel` |

**2. `DelegatedActorFilter`** runs after Bearer authentication. For a token with `act` it requires `X-Actor-Token` and checks:

- it decodes with the service's `JwtDecoder` (signature, issuer, expiry, audience);
- its `sub` equals `act.sub`;
- its `tenant_id` equals the delegated token's `tenant_id`;
- it has no `act` claim of its own.

Any failure → 401 `{"error":"invalid_delegation", ...}`.

**3. `SecurityContext`** — the context describes the **actor**; the subject comes only from the signed delegated token:

| Field | Value | Source |
|---|---|---|
| `userId`, `actorId`, `username` | `dave.assistant` | verified `X-Actor-Token` |
| `roles`, `tenantId`, `attributes` | Dave's | verified `X-Actor-Token` |
| `subjectId` | `carol.executive` | delegated token `preferred_username` |
| `isDelegated` | `true` | `act` present |
| `delegationId`, `consentId`, `purpose` | header values | `X-Delegation-*`, `X-Consent-Id` |

Delegated tokens get no Spring authorities, and Carol's roles are never used: delegation does not grant the delegator's privileges. `X-Delegated-Subject` is ignored.

**4. OPA input** (`delegate_id` = `actorId`, `delegator_id` = `subjectId`):

```json
{
  "input": {
    "user":       { "user_id": "dave.assistant", "tenant_id": "tenant-a", "roles": ["employee", "assistant", "..."] },
    "action":     "create_booking",
    "resource":   { "type": "booking", "tenant_id": "tenant-a", "user_id": "carol.executive" },
    "delegation": { "active": true, "delegate_id": "dave.assistant", "delegator_id": "carol.executive" },
    "consent":    { "valid": true, "consent_id": "c3d2e1f0-…", "scopes": ["book_travel", "view_booking", "create_expenses", "submit_expenses"] }
  }
}
```

`consent.valid` is `true` whenever a consent id is present (the BFF validated it at activation). `consent.scopes` is derived from the purpose by `OpaClient.extractScopesFromPurpose`, not read from the consent record:

| Purpose | OPA consent scopes |
|---|---|
| `book_travel` | `book_travel`, `view_booking`, `create_expenses`, `submit_expenses` |
| `approve_expenses` | `approve_expenses`, `view_expense` |
| `manage_team` | `view_booking`, `view_expense`, `approve_expenses` |
| anything else | none |

**5. Policy.**

```rego
allow if {
    input.action == "create_booking"
    is_same_tenant
    has_active_delegation    # active, delegator_id == resource.user_id, delegate_id == user.user_id
    input.consent.valid == true
    "book_travel" in input.consent.scopes
}
```

(Dave would also pass the plain `create_booking` rule through his own `employee` role; the delegation rule matters for update, submit and approve actions.)

---

## Error Scenarios

| Scenario | Status | Response |
|---|---|---|
| OPA `allow` false | 403 | `ProblemDetail`, title `Access Denied`, detail e.g. `Not authorized to create bookings` |
| OPA unreachable or malformed answer | 403 | Fails closed, same as deny |
| Tenant mismatch | 403 | OPA deny |
| Delegated action whose purpose lacks the required scope | 403 | OPA deny |
| Expense mutation when status is not `DRAFT` | 400 | Service status guard (before OPA) |
| Non-manager approves an expense | 403 | OPA deny |
| JWT missing, invalid, or wrong `aud` | 401 | Spring Security, before the controller |
| Delegated token without a valid matching `X-Actor-Token` | 401 | `invalid_delegation` from `DelegatedActorFilter` |

---

## Trying It

**Query OPA directly** (fastest way to debug a rule):

```bash
curl -s http://localhost:8181/v1/data/corporate/travel/authorization/allow \
  -H "Content-Type: application/json" -d '{
    "input": {
      "user": { "user_id": "dave.assistant", "tenant_id": "tenant-a", "roles": ["employee"] },
      "action": "create_booking",
      "resource": { "type": "booking", "tenant_id": "tenant-a", "user_id": "carol.executive" },
      "delegation": { "active": true, "delegate_id": "dave.assistant", "delegator_id": "carol.executive" },
      "consent": { "valid": true, "scopes": ["book_travel"] }
    }
  }'
# {"result":true}
```

`scripts/test-opa-policy.sh` runs a set of such checks.

**Direct booking through the gateway** (token via `scripts/get-token.sh`, which uses the `employee-bff` client):

```bash
TOKEN=$(curl -s http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d grant_type=password -d client_id=employee-bff -d client_secret=bff-service-secret-change-in-production \
  -d username=alice.employee -d password=password123 | jq -r .access_token)

curl -s -X POST http://localhost:8000/api/bookings \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{ "destination": "New York", "startDate": "2026-11-01", "endDate": "2026-11-05",
        "budget": 2500, "budgetCurrency": "USD",
        "tenantId": "ignored", "userId": "ignored", "status": "PENDING" }'
# 201; tenantId and userId in the response come from the token
```

A delegated request cannot be hand-built with headers: it needs a Keycloak-issued delegated token, which only the BFF obtains. Use the delegate journey or `scripts/end-to-end-test/run-delegation-flow.sh`.

---

## Policy Reference

`infrastructure/opa/policies/authorization.rego`, package `corporate.travel.authorization`, `default allow := false`.

| Helper | Checks |
|---|---|
| `is_same_tenant` | `user.tenant_id == resource.tenant_id` |
| `has_role(r)` | `r in user.roles` |
| `is_resource_owner` | `user.user_id == resource.user_id` |
| `has_active_delegation` | `delegation.active`, `delegator_id == resource.user_id`, `delegate_id == user.user_id` |
| `is_manager_of_user` | `manager` role and `user.user_id in resource.manager_chain` |

- **RBAC:** `employee` creates bookings and expenses; `manager` approves or rejects `SUBMITTED` expenses in the tenant; `admin` may do anything in the tenant.
- **Tenant isolation:** every `allow` rule, including the admin rule, requires `is_same_tenant`.
- **Ownership:** owners view, update and submit their own resources.
- **Delegation + consent:** delegated view needs `has_active_delegation`; delegated create/update/submit/approve also need `consent.valid` and the scope (`book_travel`, `create_expenses`, `approve_expenses`).
- **State guards:** expense update, delete and submit require `DRAFT`; approve and reject require `SUBMITTED`.
- **`delete_booking`** has no dedicated rule; only the admin rule allows it.
- **`manager_chain`** is not populated by any service today, so `is_manager_of_user` never matches in practice.
