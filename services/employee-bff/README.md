# Employee BFF

Backend-for-Frontend service implementing Keycloak RFC 8693 delegation (ADR-024), session-scoped delegation context management, and delegation-aware API aggregation for bookings and expenses.

**Port**: 8085  
**Spring Boot**: 4.1.1 / Java 25  
**Database schema**: `bff` (delegation grant store)

## Overview

The BFF is the primary entry point for frontend clients (and for manual testing of the delegation flow). Its core responsibilities:

1. **Delegation grant** — runs the delegator's Keycloak authorization (consent screen, `offline_access` + `delegation:user:<delegate>`) and stores the resulting offline token AES-256-GCM encrypted in Postgres
2. **Token exchange** — on activation, exchanges the delegator's token (carrying `may_act`) with the actor's token as `actor_token`, once per downstream audience. Issued tokens have `sub` = delegator, `act.sub` = actor
3. **Delegation context** — keeps the issued tokens in the HTTP session (never returned to the client) and applies them to every downstream call while delegation is active
4. **API aggregation** — proxies booking and expense operations, automatically applying delegation context

> Keycloak delegation (`token-exchange-delegation`) is a **preview** feature in Keycloak 26.8.

## Architecture

```
Frontend / curl
    ↓ JWT (caller's token)
Employee BFF (Port 8085)
    ├─ DelegationBffController
    │   ├─ POST /{id}/grant, GET /grant/callback   (delegator)
    │   │       ↓
    │   │   DelegationGrantService → Keycloak (delegation-grant client)
    │   │       └─ offline token → GrantTokenCipher → Postgres bff.delegation_grant
    │   │
    │   └─ POST /activate/{id}                     (delegate)
    │           ↓
    │       DelegationContextService / TokenExchangeService
    │           ├─ validates delegation (delegation-service:8083), caller must be the delegate
    │           ├─ validates consent    (consent-service:8084)
    │           ├─ refreshes delegator token from the stored grant (carries may_act)
    │           └─ KeycloakTokenExchangeClient → RFC 8693 exchange with actor_token
    │               (one token per audience: travel-service, expense-service)
    │           ↓
    │       session: DelegationContext stored
    │
    ├─ BookingBffController      /api/bff/bookings
    │       ↓ delegated token + X-Actor-Token if context active
    │   TravelServiceClient (8081)
    │
    └─ ExpenseBffController      /api/bff/expenses
            ↓ delegated token + X-Actor-Token if context active
        ExpenseServiceClient (8082)
```

## API Endpoints

### Delegation Management

| Method | Path | Caller | Purpose |
|--------|------|--------|---------|
| `POST` | `/api/bff/delegation/{id}/grant` | Delegator | Returns `{"authorizationUrl": ...}` for the Keycloak consent screen |
| `GET` | `/api/bff/delegation/grant/callback` | Browser (Keycloak redirect) | Stores the grant, redirects to the frontend with `?grant=success\|error` |
| `GET` | `/api/bff/delegation/{id}/grant` | Any | `{"delegationId": ..., "authorized": true\|false}` |
| `DELETE` | `/api/bff/delegation/{id}` | Delegator | Revokes the delegation and its Keycloak offline session (204) |
| `POST` | `/api/bff/delegation/activate/{id}` | Delegate | Activates delegation mode; 409 if the delegator has not authorized it |
| `DELETE` | `/api/bff/delegation/deactivate` | Delegate | Clears the session delegation context (204) |
| `GET` | `/api/bff/delegation/context` | Delegate | Active context, or `{"delegationActive": false}` |

The grant callback is registered as `http://localhost:3000/api/bff/delegation/grant/callback` and reaches the BFF through the Next.js `/api/bff` rewrite, together with the BFF session cookie that holds the grant `state`.

#### Activate Delegation Mode
```http
POST /api/bff/delegation/activate/{delegationId}
Authorization: Bearer <actor-JWT>
```

**Response** (tokens are not included):
```json
{
  "delegationId": "uuid",
  "actorId": "dave.assistant",
  "subjectId": "carol.executive",
  "audiences": ["travel-service", "expense-service"],
  "purpose": "book_travel",
  "consentId": "uuid",
  "expiresAt": "2026-10-08T10:15:00Z"
}
```

`expiresAt` is the earliest expiry of the issued tokens.

### Bookings (Delegation-Aware)

```
GET    /api/bff/bookings              List bookings (subject's if delegation active)
POST   /api/bff/bookings              Create booking (attributed to subject)
GET    /api/bff/bookings/{id}         Get booking
GET    /api/bff/bookings/{id}/audit   Booking audit trail
```

When delegation is active, the BFF calls travel-service with:
- `Authorization: Bearer <delegated token for travel-service>` (`sub` = Carol, `act.sub` = Dave)
- `X-Actor-Token: <Dave's own JWT>` — services verify it against `act.sub`
- `X-Delegation-Id`, and `X-Consent-Id` / `X-Delegation-Purpose` when known — audit metadata

Previously the subject was sent in an `X-Delegated-Subject` header; that header is no longer sent, and services ignore it.

### Expenses (Delegation-Aware)

```
GET    /api/bff/expenses              List expenses
POST   /api/bff/expenses              Create expense
GET    /api/bff/expenses/{id}         Get expense
GET    /api/bff/expenses/{id}/audit   Expense audit trail
POST   /api/bff/expenses/{id}/submit  Submit expense
```

Same delegation handling as bookings, with the token for `expense-service`.

### Dashboard

```
GET /api/bff/dashboard
```

Aggregates booking + expense summary for the current user (or delegation subject).

### Error Mapping

Downstream 4xx responses are passed through with their status and body; downstream 5xx become 502.

## Token Exchange Details

```
POST /realms/corporate-travel/protocol/openid-connect/token
  grant_type           = urn:ietf:params:oauth:grant-type:token-exchange
  subject_token        = <delegator access token, carrying may_act>
  subject_token_type   = urn:ietf:params:oauth:token-type:access_token
  actor_token          = <actor access token>
  actor_token_type     = urn:ietf:params:oauth:token-type:access_token
  requested_token_type = urn:ietf:params:oauth:token-type:access_token
  audience             = travel-service
```

The exchange uses Spring Security's `RestClientTokenExchangeTokenResponseClient`, called statelessly (not `OAuth2AuthorizedClientManager`, which caches per principal). All HTTP clients are `RestClient`, pinned to HTTP/1.1 (`InternalHttpClientConfig`).

### Keycloak Prerequisites

All are part of realm-as-code (`infrastructure/keycloak/config/corporate-travel.json`) or compose:

- `KC_FEATURES: token-exchange-delegation,parameterized-scopes` on the Keycloak container
- Standard token exchange enabled on the `employee-bff` client
- `delegation-grant` client and `delegation:user` client scope
- FGAP v2 delegation permissions (`keycloak-fgap` one-shot)
- `employee-bff` audience on tokens sent to the BFF

## Configuration

```yaml
# application.yml (excerpt)
server:
  port: 8085

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/corporate_travel
  flyway:
    schemas: bff
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/corporate-travel
          audiences:
            - employee-bff

keycloak:
  url: http://localhost:8080
  public-url: http://localhost:8080        # browser-facing URL for the grant redirect
  realm: corporate-travel
  client-id: employee-bff
  client-secret: bff-service-secret-change-in-production

delegation:
  audiences: [travel-service, expense-service]   # default
  grant:
    client-id: delegation-grant
    client-secret: delegation-grant-secret-change-in-production
    redirect-uri: http://localhost:3000/api/bff/delegation/grant/callback
    return-url: http://localhost:3000/delegation
    encryption-key: <base64, 32 bytes>    # demo key; override in any real deployment

services:
  travel-service-url: http://localhost:8081
  expense-service-url: http://localhost:8082
  delegation-service-url: http://localhost:8083
  consent-service-url: http://localhost:8084
```

In compose, `DELEGATION_GRANT_CLIENT_SECRET` and `DELEGATION_GRANT_ENCRYPTION_KEY` come from `KC_CLIENT_SECRET_DELEGATION_GRANT` and `BFF_GRANT_ENCRYPTION_KEY` in `.env`.

## Running

```bash
# Start infrastructure + downstream services first
podman compose up -d postgres neo4j keycloak keycloak-config keycloak-fgap opa \
  delegation-service consent-service travel-service expense-service

# Run BFF locally
./gradlew :services:employee-bff:bootRun
# Available at http://localhost:8085

# Or in compose (all services)
podman compose up -d
```

## Testing the Delegation Flow

The fastest way to verify the full delegation flow end-to-end:

```bash
./scripts/end-to-end-test/run-delegation-flow.sh
```

For a walkthrough, see [DELEGATE-JOURNEY.md](../../docs/journeys/DELEGATE-JOURNEY.md).

### Manual Quick Test

Run `./scripts/seed-data.sh` first: it creates the Carol → Dave delegation and performs Carol's Keycloak grant (`scripts/lib/delegation-grant.sh`).

```bash
# 1. Get Dave's token (employee-bff is the only client with direct access grants)
DAVE_TOKEN=$(curl -s -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d "client_id=employee-bff&client_secret=bff-service-secret-change-in-production" \
  -d "username=dave.assistant&password=password123&grant_type=password" \
  | jq -r '.access_token')

# 2. Look up the delegation ID (created by Carol)
DELEGATION_ID=$(curl -s http://localhost:8083/api/delegations/to-me \
  -H "Authorization: Bearer $DAVE_TOKEN" | jq -r '.[0].id')

# 3. Activate delegation in the BFF session (409 if Carol has not authorized it)
curl -c cookies.txt -X POST \
  "http://localhost:8085/api/bff/delegation/activate/$DELEGATION_ID" \
  -H "Authorization: Bearer $DAVE_TOKEN"

# 4. Create a booking — owned by Carol, created by Dave
curl -b cookies.txt -X POST http://localhost:8085/api/bff/bookings \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"placeholder","userId":"placeholder","destination":"London","startDate":"2026-12-01","endDate":"2026-12-05","businessPurpose":"Client review","budget":80000,"budgetCurrency":"INR","status":"DRAFT"}'

# 5. Check audit trail — actorId=dave.assistant, subjectId=carol.executive
BOOKING_ID=<id from step 4>
curl -b cookies.txt http://localhost:8085/api/bff/bookings/$BOOKING_ID/audit \
  -H "Authorization: Bearer $DAVE_TOKEN"
```

## API Documentation

- Swagger UI: http://localhost:8085/swagger-ui.html
- OpenAPI spec: http://localhost:8085/api-docs

## Health Check

```bash
curl http://localhost:8085/actuator/health
```

## Project Structure

```
employee-bff/
├── src/main/java/com/corporate/travel/bff/
│   ├── EmployeeBffApplication.java
│   ├── client/
│   │   ├── TravelServiceClient.java
│   │   ├── ExpenseServiceClient.java
│   │   ├── DelegationServiceClient.java
│   │   ├── ConsentServiceClient.java
│   │   ├── DelegationRequestHeaders.java     (delegated token + X-Actor-Token + audit headers)
│   │   └── KeycloakTokenExchangeClient.java  (RFC 8693 exchange with actor_token)
│   ├── config/
│   │   ├── BffProperties.java
│   │   ├── RestClientConfig.java
│   │   └── SecurityConfig.java
│   ├── controller/
│   │   ├── BookingBffController.java
│   │   ├── ExpenseBffController.java
│   │   ├── DelegationBffController.java
│   │   └── DashboardBffController.java
│   ├── grant/
│   │   ├── DelegationGrantService.java       (grant ceremony, revocation)
│   │   ├── DelegationGrantRepository.java
│   │   ├── GrantTokenCipher.java             (AES-256-GCM)
│   │   └── KeycloakGrantClient.java
│   ├── model/
│   │   └── DelegationContext.java
│   └── service/
│       ├── DelegationContextService.java     (validate + activate delegation)
│       ├── TokenExchangeService.java         (per-audience delegated exchange)
│       └── ApiAggregationService.java        (dashboard aggregation)
└── src/main/resources/
    ├── application.yml
    ├── application-docker.yml
    └── db/migration/V1__delegation_grants.sql
```

## Related Services

- **Delegation Service** (8083) — validates delegation records during activation
- **Consent Service** (8084) — validates consent scope before token exchange
- **Travel Service** (8081) — receives delegated booking requests
- **Expense Service** (8082) — receives delegated expense requests
- **Keycloak** (8080) — grant, token refresh and delegated token exchange

## Related Documentation

- [DELEGATE-JOURNEY.md](../../docs/journeys/DELEGATE-JOURNEY.md) — delegation walkthrough
- [ADR-024: Keycloak Native RFC 8693 Delegation](../../architecture-decision-records/ADR-024:%20Adopt%20Keycloak%20Native%20RFC%208693%20Delegation.md)
- [ADR-004: OAuth 2.0 Token Exchange](../../architecture-decision-records/ADR-004:%20Adopt%20OAuth%202.0%20Token%20Exchange%20for%20Delegated%20Identity.md)
- [ADR-018: BFF Strategy](../../architecture-decision-records/ADR-018:%20Backend-for-Frontend%20(BFF)%20Strategy.md)
