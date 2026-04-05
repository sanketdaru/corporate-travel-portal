# Employee BFF

Backend-for-Frontend service implementing Standard Token Exchange V2 (RFC 8693), session-scoped delegation context management, and delegation-aware API aggregation for bookings and expenses.

**Port**: 8085  
**Spring Boot**: 3.2.2 / Java 17

## Overview

The BFF is the primary entry point for frontend clients (and for manual testing of the delegation flow). Its core responsibilities:

1. **Token Exchange** — exchanges an actor's JWT for an audience-scoped delegation token via Keycloak Standard Token Exchange V2
2. **Delegation Context** — stores the active delegation context in the HTTP session; injects delegation headers (`X-Delegated-Subject`, `X-Delegation-Id`, `X-Consent-Id`, `X-Delegation-Purpose`) on every downstream call while delegation is active
3. **API Aggregation** — proxies booking and expense operations, automatically applying delegation context

## Architecture

```
Frontend / curl
    ↓ JWT (actor's token)
Employee BFF (Port 8085)
    ├─ DelegationBffController   POST /api/bff/delegation/activate/{id}
    │       ↓
    │   DelegationContextService
    │       ├─ validates delegation (delegation-service:8083)
    │       ├─ validates consent   (consent-service:8084)
    │       └─ TokenExchangeService → Keycloak Standard Token Exchange V2
    │           (actor token → audience-scoped delegation token)
    │       ↓
    │   session: DelegationContext stored
    │
    ├─ BookingBffController      POST /api/bff/bookings
    │       ↓ injects delegation headers if context active
    │   TravelServiceClient (8081)
    │
    └─ ExpenseBffController      POST /api/bff/expenses
            ↓ injects delegation headers if context active
        ExpenseServiceClient (8082)
```

## API Endpoints

### Delegation Management

#### Activate Delegation Mode
```http
POST /api/bff/delegation/activate/{delegationId}?audience=travel-service
Authorization: Bearer <actor-JWT>
```

Performs Standard Token Exchange V2. Stores `DelegationContext` (delegation token, subject, delegation ID, consent ID) in the HTTP session. All subsequent booking/expense calls will use this context automatically.

**Response**:
```json
{
  "delegationId": "uuid",
  "delegatorId": "carol.executive",
  "delegateId": "dave.assistant",
  "delegationToken": "<exchanged-JWT>",
  "subjectId": "carol.executive",
  "consentId": "uuid",
  "purpose": "book_travel"
}
```

#### Deactivate Delegation Mode
```http
DELETE /api/bff/delegation/deactivate
Authorization: Bearer <actor-JWT>
```

Clears the session delegation context. Subsequent calls revert to the actor's own identity.

### Bookings (Delegation-Aware)

```
GET    /api/bff/bookings              List bookings (subject's if delegation active)
POST   /api/bff/bookings              Create booking (attributed to subject)
GET    /api/bff/bookings/{id}         Get booking
PUT    /api/bff/bookings/{id}/status  Update booking status
DELETE /api/bff/bookings/{id}         Delete booking
```

When delegation is active, the BFF uses the exchanged delegation token and injects:
- `X-Delegated-Subject: carol.executive`
- `X-Delegation-Id: <uuid>`
- `X-Consent-Id: <uuid>`

### Expenses (Delegation-Aware)

```
GET    /api/bff/expenses              List expenses
POST   /api/bff/expenses              Create expense
GET    /api/bff/expenses/{id}         Get expense
PUT    /api/bff/expenses/{id}         Update expense
```

Same delegation header injection as bookings.

### Dashboard

```
GET /api/bff/dashboard
```

Aggregates booking + expense summary for the current user (or delegation subject).

## Token Exchange Details

The BFF uses Keycloak Standard Token Exchange V2 (RFC 8693):

```
POST /realms/corporate-travel/protocol/openid-connect/token
  grant_type           = urn:ietf:params:oauth:grant-type:token-exchange
  subject_token        = <actor JWT>
  subject_token_type   = urn:ietf:params:oauth:token-type:access_token
  requested_token_type = urn:ietf:params:oauth:token-type:access_token
  audience             = travel-service
  requested_subject    = carol.executive
```

The resulting token has `sub=dave.assistant` (actor) but is scoped for `travel-service` and carries `preferred_username=dave.assistant`. The `X-Delegated-Subject` header carries `carol.executive` so downstream services can distinguish actor from subject.

### Keycloak Prerequisites

- `KC_FEATURES=token-exchange-standard` on the Keycloak container
- Standard token exchange enabled on the `employee-bff` client
- `aud` mapper on `employee-bff` scope → `travel-service` audience
- `preferred_username` mapper on `user-attributes` scope

## Configuration

```yaml
# application.yml
server:
  port: 8085

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/corporate-travel

services:
  travel-service:
    url: http://localhost:8081
  expense-service:
    url: http://localhost:8082
  delegation-service:
    url: http://localhost:8083
  consent-service:
    url: http://localhost:8084
  keycloak:
    token-url: http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token
    client-id: employee-bff
    client-secret: <secret>
```

## Running

```bash
# Start all infrastructure + services first
docker-compose up -d postgres neo4j keycloak opa delegation-service consent-service travel-service expense-service

# Run BFF locally
./gradlew :services:employee-bff:bootRun
# Available at http://localhost:8085

# Or via Docker Compose (all services)
docker-compose up -d
```

## Testing the Delegation Flow

The fastest way to verify the full delegation flow end-to-end:

```bash
./scripts/end-to-end-test/run-delegation-flow.sh
```

For a manual walkthrough step-by-step, see [DELEGATION-FLOW.md](../../DELEGATION-FLOW.md).

### Manual Quick Test

```bash
# 1. Get Dave's token
DAVE_TOKEN=$(curl -s -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d "client_id=employee-portal&username=dave.assistant&password=password123&grant_type=password" \
  | jq -r '.access_token')

# 2. Look up the delegation ID (created by Carol earlier)
DELEGATION_ID=$(curl -s http://localhost:8083/api/delegations/to-me \
  -H "Authorization: Bearer $DAVE_TOKEN" | jq -r '.[0].id')

# 3. Activate delegation in BFF session
curl -c cookies.txt -X POST \
  "http://localhost:8085/api/bff/delegation/activate/$DELEGATION_ID?audience=travel-service" \
  -H "Authorization: Bearer $DAVE_TOKEN"

# 4. Create a booking — will be attributed to Carol
curl -b cookies.txt -X POST http://localhost:8085/api/bff/bookings \
  -H "Authorization: Bearer $DAVE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bookingType":"FLIGHT","destination":"London","startDate":"2026-07-01","endDate":"2026-07-05","totalAmount":800.00}'

# 5. Check audit trail — should show actorId=dave.assistant, subjectId=carol.executive
BOOKING_ID=<id from step 4>
curl http://localhost:8081/api/bookings/$BOOKING_ID/audit \
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
│   │   └── ConsentServiceClient.java          (via DelegationContextService)
│   ├── config/
│   │   └── WebClientConfig.java
│   ├── controller/
│   │   ├── BookingBffController.java
│   │   ├── ExpenseBffController.java
│   │   ├── DelegationBffController.java
│   │   └── DashboardBffController.java
│   ├── model/
│   │   └── DelegationContext.java
│   └── service/
│       ├── DelegationContextService.java      (validate + activate delegation)
│       ├── TokenExchangeService.java          (RFC 8693 exchange)
│       └── ApiAggregationService.java         (dashboard aggregation)
└── src/main/resources/
    ├── application.yml
    └── application-docker.yml
```

## Related Services

- **Delegation Service** (8083) — validates delegation records during activation
- **Consent Service** (8084) — validates consent scope before token exchange
- **Travel Service** (8081) — receives delegation-annotated booking requests
- **Expense Service** (8082) — receives delegation-annotated expense requests
- **Keycloak** (8080) — performs the actual token exchange

## Related Documentation

- [DELEGATION-FLOW.md](../../DELEGATION-FLOW.md) — step-by-step delegation walkthrough
- [ADR-004: OAuth 2.0 Token Exchange](../../architecture-decision-records/ADR-004:%20Adopt%20OAuth%202.0%20Token%20Exchange%20for%20Delegated%20Identity.md)
- [ADR-018: BFF Strategy](../../architecture-decision-records/ADR-018:%20Backend-for-Frontend%20(BFF)%20Strategy.md)
