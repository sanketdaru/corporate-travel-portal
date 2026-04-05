# Delegation Flow Guide

This guide walks through the complete end-to-end delegation flow implemented in this platform: **Carol (executive) delegates travel booking authority to Dave (assistant)**.

The flow covers all five layers: delegation setup, consent setup, token exchange, delegated API call, and audit trail verification.

For automated validation, run:

```bash
./scripts/end-to-end-test/run-delegation-flow.sh
# Expected: 71/71 assertions passing
```

---

## Prerequisites

All services running:

```bash
docker-compose up -d
docker-compose ps   # all services should be "healthy"
```

---

## Conceptual Overview

```
Carol (executive)                          Dave (assistant)
    │                                           │
    │ 1. POST /api/delegations                  │
    │    (grant Dave book_travel authority)     │
    │                                           │
    │ 2. POST /api/consents                     │
    │    (bind consent: scope=create_bookings)  │
    │                                           │
    │                               3. GET token from Keycloak
    │                               4. POST /api/bff/delegation/activate
    │                                  → BFF performs Token Exchange V2
    │                                  → session: DelegationContext stored
    │                                           │
    │                               5. POST /api/bff/bookings
    │                                  (BFF injects delegation headers)
    │                                           │
    │                                  travel-service receives:
    │                                    Authorization: Bearer <delegation-token>
    │                                    X-Delegated-Subject: carol.executive
    │                                    X-Delegation-Id: <uuid>
    │                                    X-Consent-Id: <uuid>
    │                                           │
    │ 6. Booking created under Carol ←──────────┘
    │    booking.userId = carol.executive
    │    booking.createdBy = dave.assistant
    │
    │ 7. Audit record:
    │    actorId=dave.assistant
    │    subjectId=carol.executive
    │    delegationId=<uuid>
    │    consentId=<uuid>
```

---

## Step-by-Step Walkthrough

### Step 1: Get Tokens

```bash
# Carol's token (to create delegation and consent)
CAROL_TOKEN=$(curl -s -X POST \
  "http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token" \
  -d "client_id=employee-portal" \
  -d "username=carol.executive" \
  -d "password=password123" \
  -d "grant_type=password" \
  | jq -r '.access_token')

# Dave's token (to activate delegation and act as Carol)
DAVE_TOKEN=$(curl -s -X POST \
  "http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token" \
  -d "client_id=employee-portal" \
  -d "username=dave.assistant" \
  -d "password=password123" \
  -d "grant_type=password" \
  | jq -r '.access_token')
```

### Step 2: Carol Creates a Delegation

Carol grants Dave authority to book travel on her behalf:

```bash
DELEGATION=$(curl -s -X POST http://localhost:8083/api/delegations \
  -H "Authorization: Bearer $CAROL_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "delegateId": "dave.assistant",
    "purpose": "book_travel",
    "scopes": ["view_bookings", "create_bookings"],
    "expiresAt": "2027-01-01T00:00:00"
  }')

DELEGATION_ID=$(echo $DELEGATION | jq -r '.id')
echo "Delegation ID: $DELEGATION_ID"
```

OPA authorizes this: Carol is the delegator (resource owner) in her tenant.

### Step 3: Carol Creates Consent

Carol binds consent for `book_travel` / `create_bookings`, linked to the delegation:

```bash
CONSENT=$(curl -s -X POST http://localhost:8084/api/consents \
  -H "Authorization: Bearer $CAROL_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"grantorId\": \"carol.executive\",
    \"granteeId\": \"dave.assistant\",
    \"delegationId\": \"$DELEGATION_ID\",
    \"purpose\": \"book_travel\",
    \"scopes\": [\"view_bookings\", \"create_bookings\"],
    \"dataCategories\": [\"travel_data\"]
  }")

CONSENT_ID=$(echo $CONSENT | jq -r '.id')
echo "Consent ID: $CONSENT_ID"
```

### Step 4: Dave Activates Delegation Mode (Token Exchange)

Dave calls the BFF to activate delegation. The BFF:
1. Validates the delegation is active (delegation-service)
2. Validates consent covers `create_bookings` (consent-service)
3. Performs Standard Token Exchange V2 with Keycloak:
   - `subject_token` = Dave's JWT
   - `requested_subject` = carol.executive
   - `audience` = travel-service
4. Stores `DelegationContext` in the HTTP session

```bash
CONTEXT=$(curl -s -c cookies.txt -X POST \
  "http://localhost:8085/api/bff/delegation/activate/$DELEGATION_ID?audience=travel-service" \
  -H "Authorization: Bearer $DAVE_TOKEN")

echo $CONTEXT | jq '.'
# {
#   "delegationId": "...",
#   "delegatorId": "carol.executive",
#   "delegateId": "dave.assistant",
#   "delegationToken": "<exchanged JWT>",
#   "subjectId": "carol.executive",
#   "consentId": "...",
#   "purpose": "book_travel"
# }
```

> Note: `-c cookies.txt` saves the session cookie. Use `-b cookies.txt` on subsequent BFF calls to carry the session.

### Step 5: Dave Creates a Booking as Carol

Dave calls the BFF booking endpoint. The BFF detects an active delegation context in the session and automatically:
- Uses the exchanged delegation token (not Dave's original JWT)
- Injects `X-Delegated-Subject: carol.executive`
- Injects `X-Delegation-Id: <uuid>`
- Injects `X-Consent-Id: <uuid>`

```bash
BOOKING=$(curl -s -b cookies.txt -X POST http://localhost:8085/api/bff/bookings \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bookingType": "FLIGHT",
    "destination": "London",
    "startDate": "2026-07-01",
    "endDate": "2026-07-05",
    "totalAmount": 800.00
  }')

BOOKING_ID=$(echo $BOOKING | jq -r '.id')
echo "Booking ID: $BOOKING_ID"

# The booking is saved as:
echo $BOOKING | jq '{userId, createdBy}'
# {
#   "userId": "carol.executive",   ← subject (who owns the booking)
#   "createdBy": "dave.assistant"  ← actor (who created it)
# }
```

### Step 6: Verify the Audit Trail

```bash
curl -s http://localhost:8081/api/bookings/$BOOKING_ID/audit \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  | jq '.[0]'

# Expected output:
# {
#   "action": "CREATE",
#   "actorId": "dave.assistant",
#   "subjectId": "carol.executive",
#   "delegationId": "<uuid>",
#   "consentId": "<uuid>",
#   "tenantId": "tenant-a",
#   "timestamp": "..."
# }
```

### Step 7: Deactivate Delegation Mode

```bash
curl -s -b cookies.txt -X DELETE \
  http://localhost:8085/api/bff/delegation/deactivate \
  -H "Authorization: Bearer $DAVE_TOKEN"
# HTTP 204 No Content

# Subsequent BFF calls now use Dave's own identity
```

---

## What Happens Inside the BFF (Token Exchange Detail)

When `POST /api/bff/delegation/activate/{delegationId}` is called:

```
DelegationContextService.activateDelegation(delegationId, daveToken, daveUserId, "travel-service", session)
    │
    ├─ DelegationServiceClient.getDelegation(delegationId)
    │    → validates delegation is ACTIVE, delegateId == dave.assistant
    │
    ├─ ConsentServiceClient.validateConsent(carolId, daveId, "book_travel", ["create_bookings"])
    │    → validates consent is ACTIVE and covers the required scope
    │
    └─ TokenExchangeService.exchangeToken(daveToken, "travel-service", "carol.executive")
         │
         POST /realms/corporate-travel/protocol/openid-connect/token
           grant_type           = urn:ietf:params:oauth:grant-type:token-exchange
           subject_token        = <Dave's JWT>
           subject_token_type   = urn:ietf:params:oauth:token-type:access_token
           requested_token_type = urn:ietf:params:oauth:token-type:access_token
           audience             = travel-service
           requested_subject    = carol.executive
         │
         ← returns delegation token (sub=dave.assistant, aud=travel-service)
         │
         DelegationContext stored in HTTP session
```

---

## What Happens Inside Travel Service (Authorization Detail)

When `POST /api/bookings` arrives at travel-service with delegation headers:

```
JwtAuthenticationConverter.extractSecurityContext(jwt, httpRequest)
    │
    ├─ userId     = jwt.sub   = "dave.assistant"        (actor)
    ├─ subjectId  = header X-Delegated-Subject          = "carol.executive"
    ├─ delegationId = header X-Delegation-Id            = "<uuid>"
    ├─ consentId  = header X-Consent-Id                 = "<uuid>"
    └─ tenantId   = jwt claim "tenant_id"               = "tenant-a"
    │
BookingServiceImpl.createBooking(request, securityContext)
    │
    ├─ OpaClient.authorize(context, "create_booking", resource)
    │    OPA checks:
    │    - input.token.tenant_id == resource.tenant_id  ✓ (tenant isolation)
    │    - has_role("employee") with realm_access.roles  ✓ (dave has assistant role)
    │    - (delegation path) is_active_delegate with scope "create_bookings" ✓
    │
    ├─ booking.userId    = context.subjectId  = "carol.executive"
    ├─ booking.createdBy = context.userId     = "dave.assistant"
    ├─ booking.tenantId  = context.tenantId   = "tenant-a"
    │
    └─ BookingAuditService.recordAction(bookingId, "CREATE", context)
         → booking_audit row: actorId=dave, subjectId=carol, delegationId, consentId
```

---

## OPA Policy Reference

The relevant rules in `infrastructure/opa/policies/authorization.rego`:

```rego
# Any active delegate with the required scope can act
is_active_delegate {
    input.delegation != null
    input.delegation.active == true
    input.delegation.delegate_id == input.token.sub
    required_scope := get_required_scope(input.action)
    required_scope != ""
    required_scope in input.delegation.scopes
}

# Owner rule for update/delete
is_resource_owner {
    input.resource.user_id == input.token.sub
}

# update_booking: owner OR active delegate
allow {
    input.action == "update_booking"
    tenant_matches
    is_resource_owner
}

allow {
    input.action == "update_booking"
    tenant_matches
    is_active_delegate
}
```

---

## Keycloak Configuration Notes

The following Keycloak configuration is required for token exchange to work. It is already applied in the committed `realm-export.json`.

| Setting | Location | Value |
|---------|----------|-------|
| Standard Token Exchange | `docker-compose.yml` | `KC_FEATURES=token-exchange-standard` |
| Token exchange on client | `employee-bff` client → Advanced | Standard Token Exchange: Enabled |
| Audience mapper | `employee-bff` client scope | `aud` → `travel-service` |
| Username in token | `user-attributes` scope | `preferred_username` mapper |
| Roles in token | `user-attributes` scope | `oidc-usermodel-realm-role-mapper` |

If `realm_access.roles` is missing from tokens (e.g. after a realm re-import), direct (non-delegated) booking/expense creation will 403 because OPA's `has_role("employee")` check fails.

---

## Troubleshooting

**Token exchange returns 400**
- Verify `KC_FEATURES=token-exchange-standard` in `docker-compose.yml`
- Verify standard token exchange is enabled on the `employee-bff` Keycloak client (not the deprecated per-client flag)

**Delegation activation returns 403 (OPA)**
- Verify the delegation record is ACTIVE: `GET /api/delegations/{id}`
- Verify consent is ACTIVE and covers `create_bookings`: `GET /api/consents/{id}`

**Booking created with wrong userId (actor instead of subject)**
- The controller is not using the header-aware `extractSecurityContext(jwt, httpRequest)` overload
- Check `BookingController.createBooking` passes `HttpServletRequest httpRequest` and calls the two-arg overload

**Audit row missing delegationId**
- Verify `X-Delegation-Id` header is set by BFF (`TravelServiceClient`)
- Verify `JwtAuthenticationConverter` reads `X-Delegation-Id` from the request

**`realm_access.roles` missing → all users 403**
- Go to Keycloak admin → `corporate-travel` realm → Client Scopes → `user-attributes` → Mappers
- Add mapper: Type = `User Realm Role`, Token Claim Name = `realm_access.roles`, Multivalued = on

---

## Related Documentation

- [README.md](README.md) — system overview and quick start
- [IMPLEMENTATION.md](IMPLEMENTATION.md) — implementation patterns and architecture
- [services/employee-bff/README.md](services/employee-bff/README.md) — BFF service reference
- [services/travel-service/README.md](services/travel-service/README.md) — travel service audit endpoints
- [ADR-004: OAuth 2.0 Token Exchange](architecture-decision-records/ADR-004:%20Adopt%20OAuth%202.0%20Token%20Exchange%20for%20Delegated%20Identity.md)
- [ADR-005: Consent and Purpose Binding](architecture-decision-records/ADR-005:%20Implement%20External%20Consent%20and%20Purpose%20Binding%20Service.md)
- [ADR-011: Audit and Compliance Ledger](architecture-decision-records/ADR-011:%20Implement%20Comprehensive%20Audit%20and%20Compliance%20Ledger.md)
- [ADR-018: BFF Strategy](architecture-decision-records/ADR-018:%20Backend-for-Frontend%20(BFF)%20Strategy.md)
