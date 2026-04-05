# Travel Service

Travel Service manages travel bookings (flights, hotels, car rentals) with multi-tenant support, OPA-based authorization, delegation-aware identity propagation, and a full audit trail.

**Port**: 8081  
**Database schema**: `travel`  
**Spring Boot**: 3.2.2 / Java 17

## Features

- Bookings CRUD with multi-tenant data isolation
- OPA authorization: tenant isolation, RBAC, resource ownership, delegation rules
- Delegation-aware via `X-Delegated-Subject` / `X-Delegation-Id` / `X-Consent-Id` headers
- Audit trail on every mutation — `actorId`, `subjectId`, `delegationId`, `consentId`
- Flyway-managed schema migrations (V1 baseline + V2 audit tables)
- OpenAPI / Swagger UI

## API Endpoints

### Bookings

```bash
POST   /api/bookings                  Create booking
GET    /api/bookings                  List my bookings
GET    /api/bookings/{id}             Get booking
PUT    /api/bookings/{id}/status      Update booking status
DELETE /api/bookings/{id}             Delete booking
```

#### Create Booking
```bash
curl -X POST http://localhost:8081/api/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bookingType": "FLIGHT",
    "destination": "New York",
    "startDate": "2026-06-01",
    "endDate": "2026-06-05",
    "totalAmount": 500.00
  }'
```

#### Delegated Booking (via BFF)
When the Employee BFF calls this endpoint on behalf of a delegate, it injects:
- `X-Delegated-Subject: carol.executive` — the human principal the booking belongs to
- `X-Delegation-Id: <uuid>` — the delegation record
- `X-Consent-Id: <uuid>` — the consent record

The service records `actorId=dave.assistant` and `subjectId=carol.executive` in the booking and audit rows.

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
# Start infrastructure first
docker-compose up -d postgres neo4j keycloak opa

# Run locally
./gradlew :services:travel-service:bootRun
# Available at http://localhost:8081

# Or via Docker Compose (all services)
docker-compose up -d
docker-compose logs -f travel-service
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
          issuer-uri: http://keycloak:8080/realms/corporate-travel
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

- **V1** — `travel.bookings` table (CRUD fields, `user_id`, `created_by`, `tenant_id`)
- **V2** — `travel.booking_audit` table (`actor_id`, `subject_id`, `delegation_id`, `consent_id`, `tenant_id`, `action`, `timestamp`, `details`)

## Security

- JWT authentication via Keycloak (`realm_access.roles` required for `has_role("employee")` in OPA)
- OPA authorization checked before every operation:
  - `create_booking` — `has_role("employee")` + tenant match
  - `view_booking` — tenant isolation + ownership or delegation
  - `update_booking` — `is_resource_owner` or `is_active_delegate` with `book_travel` scope
  - `delete_booking` — same as update
- Mutation endpoints (`PUT`, `DELETE`) use the header-aware `extractSecurityContext(jwt, httpRequest)` overload to pick up delegation headers

## API Documentation

- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI spec: http://localhost:8081/api-docs

## Health Check

```bash
curl http://localhost:8081/actuator/health
```

## Architecture

```
BookingController (REST — extracts SecurityContext with delegation headers)
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
  -d "client_id=employee-portal&username=alice.employee&password=password123&grant_type=password" \
  | jq -r '.access_token')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/bookings
```

## Related Services

- **Employee BFF** (8085) — performs token exchange and injects delegation headers before calling this service
- **Delegation Service** (8083) — stores the delegation records referenced by `X-Delegation-Id`
- **Consent Service** (8084) — stores the consent records referenced by `X-Consent-Id`
- **API Gateway** (8000) — routes `/api/bookings/**` to this service

## Related Documentation

- [DELEGATION-FLOW.md](../../DELEGATION-FLOW.md) — end-to-end delegation walkthrough
- [ADR-011: Audit and Compliance Ledger](../../architecture-decision-records/ADR-011:%20Implement%20Comprehensive%20Audit%20and%20Compliance%20Ledger.md)
- [ADR-004: OAuth 2.0 Token Exchange](../../architecture-decision-records/ADR-004:%20Adopt%20OAuth%202.0%20Token%20Exchange%20for%20Delegated%20Identity.md)
