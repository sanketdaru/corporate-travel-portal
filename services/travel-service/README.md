# Travel Service

Travel Service manages travel authorizations (bookings: destination, dates, business purpose, approved budget) with multi-tenant support, OPA-based authorization, delegation-aware identity propagation, and a full audit trail.

**Port**: 8081  
**Database schema**: `travel`  
**Spring Boot**: 4.1.1 / Java 25

## Features

- Bookings CRUD with multi-tenant data isolation
- OPA authorization: tenant isolation, RBAC, resource ownership, delegation rules
- Delegation-aware via RFC 8693 delegated tokens (`sub` = delegator, `act.sub` = actor) and a verified `X-Actor-Token` (ADR-024)
- Accepts only tokens with `aud` containing `travel-service`
- Audit trail on every mutation — `actorId`, `subjectId`, `delegationId`, `consentId`
- Flyway-managed schema migrations (V1–V4)
- OpenAPI / Swagger UI

## API Endpoints

### Bookings

```bash
POST   /api/bookings                  Create booking
GET    /api/bookings                  List my bookings
GET    /api/bookings/all              List all tenant bookings (admin)
GET    /api/bookings/{id}             Get booking
GET    /api/bookings/{id}/budget      Budget and currency (used by expense-service)
PUT    /api/bookings/{id}/status      Update booking status
DELETE /api/bookings/{id}             Delete booking
```

#### Create Booking
```bash
curl -X POST http://localhost:8081/api/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "placeholder",
    "userId": "placeholder",
    "destination": "New York",
    "startDate": "2026-12-01",
    "endDate": "2026-12-05",
    "businessPurpose": "Client meetings",
    "budget": 120000,
    "budgetCurrency": "INR",
    "status": "DRAFT"
  }'
```

`tenantId` and `userId` are required by validation but overwritten from the token.

#### Delegated Booking (via BFF)
When the Employee BFF calls this endpoint for a delegate, it sends:
- `Authorization: Bearer <delegated token>` — `sub` = carol.executive (delegator), `act.sub` = dave.assistant (actor), `aud` = travel-service
- `X-Actor-Token: <Dave's own JWT>` — verified by `DelegatedActorFilter` against `act.sub`
- `X-Delegation-Id`, `X-Consent-Id` — audit metadata

The booking is owned by Carol (`userId`) and created by Dave (`createdBy`); the audit row records `actorId=dave.assistant`, `subjectId=carol.executive`. OPA authorizes with Dave's identity and roles. An `X-Delegated-Subject` header is ignored.

### Audit Trail

```bash
GET /api/bookings/{id}/audit
```

Returns the full audit trail for a booking, ordered by timestamp descending.

**Example response**:
```json
[
  {
    "id": "...",
    "bookingId": "...",
    "action": "CREATE",
    "actorId": "dave.assistant",
    "subjectId": "carol.executive",
    "delegationId": "...",
    "consentId": "...",
    "tenantId": "tenant-a",
    "timestamp": "2026-04-03T10:15:00Z"
  }
]
```

Recorded actions: `CREATE`, `STATUS_CHANGE`, `DELETE`

## Building

```bash
# From project root
./gradlew :services:travel-service:build

# Skip tests
./gradlew :services:travel-service:build -x test
```

## Running

```bash
# Start infrastructure first (keycloak-config applies the realm)
podman compose up -d postgres keycloak keycloak-config opa

# Run locally
./gradlew :services:travel-service:bootRun
# Available at http://localhost:8081

# Or in compose (all services)
podman compose up -d
podman compose logs -f travel-service
```

## Configuration

```yaml
# application-docker.yml (Docker environment)
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/corporate_travel
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/corporate-travel   # token iss
          jwk-set-uri: http://keycloak:8080/realms/corporate-travel/protocol/openid-connect/certs
          audiences: [travel-service]   # from application.yml
server:
  port: 8081
opa:
  url: http://opa:8181
```

### Environment Variables
- `SPRING_PROFILES_ACTIVE=docker` — activates Docker profile
- `SPRING_DATASOURCE_URL` — PostgreSQL URL
- `OPA_URL` — OPA server URL

## Database Schema

Managed by Flyway:

- **V1** — `travel.bookings` and `travel.booking_audit` tables
- **V2** — `delegation_id`, `consent_id`, `tenant_id` on `booking_audit`
- **V3** — booking becomes a travel authorization: drops `booking_type` / `total_amount`, adds `budget` / `budget_currency`
- **V4** — `business_purpose`, `notes`

See [FLYWAY-MIGRATION.md](./FLYWAY-MIGRATION.md).

## Security

- JWT authentication via Keycloak; `aud` must contain `travel-service` (`realm_access.roles` required for `has_role("employee")` in OPA)
- OPA authorization checked before every operation:
  - `create_booking` — `has_role("employee")` + tenant match
  - `view_booking` — tenant isolation + ownership or delegation
  - `update_booking` — `is_resource_owner` or `is_active_delegate` with `book_travel` scope
  - `delete_booking` — same as update
- All endpoints use `extractSecurityContext(jwt, request)`, which resolves delegation from the token's `act` claim and the verified actor token

## API Documentation

- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI spec: http://localhost:8081/api-docs

## Health Check

```bash
curl http://localhost:8081/actuator/health
```

## Architecture

```
BookingController (REST — extracts SecurityContext, delegation from act + X-Actor-Token)
    ↓
BookingServiceImpl (OPA authorize → business logic → audit write, same @Transactional)
    ↓
BookingRepository (Spring Data JPA)   BookingAuditRepository
    ↓                                       ↓
PostgreSQL travel.bookings          PostgreSQL travel.booking_audit
```

## Testing

```bash
# Unit tests
./gradlew :services:travel-service:test

# Manual — get token then call API
TOKEN=$(curl -s -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d "client_id=employee-bff&client_secret=bff-service-secret-change-in-production" \
  -d "username=alice.employee&password=password123&grant_type=password" \
  | jq -r '.access_token')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/bookings
```

## Related Services

- **Employee BFF** (8085) — performs the delegated token exchange and sends the delegated token + `X-Actor-Token`
- **Delegation Service** (8083) — stores the delegation records referenced by `X-Delegation-Id`
- **Consent Service** (8084) — stores the consent records referenced by `X-Consent-Id`
- **API Gateway** (8000) — routes `/api/bookings/**` to this service

## Related Documentation

- [DELEGATE-JOURNEY.md](../../docs/journeys/DELEGATE-JOURNEY.md) — delegation walkthrough
- [ADR-024: Keycloak Native RFC 8693 Delegation](../../architecture-decision-records/ADR-024:%20Adopt%20Keycloak%20Native%20RFC%208693%20Delegation.md)
- [ADR-011: Audit and Compliance Ledger](../../architecture-decision-records/ADR-011:%20Implement%20Comprehensive%20Audit%20and%20Compliance%20Ledger.md)
- [ADR-004: OAuth 2.0 Token Exchange](../../architecture-decision-records/ADR-004:%20Adopt%20OAuth%202.0%20Token%20Exchange%20for%20Delegated%20Identity.md)
