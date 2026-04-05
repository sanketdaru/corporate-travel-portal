# Expense Service

A Spring Boot microservice for managing employee expense reports in the Corporate Travel & Expense Portal.

**Port**: 8082  
**Database schema**: `expense`  
**Spring Boot**: 3.2.2 / Java 17

## Features

- Expense report management: create, read, update, delete
- Line item support (meals, travel, accommodation, etc.)
- Approval workflow: `DRAFT → SUBMITTED → APPROVED/REJECTED → PAID`
- Multi-tenant data isolation
- Delegation-aware — assistants can manage expenses on behalf of executives
- OPA authorization: RBAC, tenant isolation, ownership, delegation scope
- Full audit trail on every mutation (`actorId`, `subjectId`, `delegationId`, `consentId`)
- Flyway-managed schema migrations
- OpenAPI / Swagger UI

## API Endpoints

### Expense Management
```
POST   /api/expenses                          Create expense report
GET    /api/expenses                          List expenses (tenant-filtered)
GET    /api/expenses/{id}                     Get expense
PUT    /api/expenses/{id}                     Update expense
DELETE /api/expenses/{id}                     Delete expense
```

### Expense Items
```
POST   /api/expenses/{expenseId}/items        Add line item
PUT    /api/expenses/{expenseId}/items/{id}   Update line item
DELETE /api/expenses/{expenseId}/items/{id}   Remove line item
```

### Workflow Operations
```
POST   /api/expenses/{id}/submit              Submit for approval
POST   /api/expenses/{id}/approve             Approve (managers only)
POST   /api/expenses/{id}/reject              Reject (managers only)
POST   /api/expenses/{id}/pay                 Mark as paid (finance only)
```

### Audit Trail
```
GET    /api/expenses/{id}/audit               Full audit trail for an expense
```

#### Audit Trail Response Example
```json
[
  {
    "id": "...",
    "expenseId": "...",
    "action": "CREATE",
    "actorId": "dave.assistant",
    "subjectId": "carol.executive",
    "delegationId": "...",
    "consentId": "...",
    "tenantId": "tenant-a",
    "timestamp": "2026-04-03T10:20:00Z"
  }
]
```

Recorded actions: `CREATE`, `UPDATE`, `DELETE`, `SUBMIT`, `APPROVE`, `REJECT`

## Configuration

```yaml
# application-docker.yml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/corporate_travel
  flyway:
    enabled: true
    schemas: expense
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: expense
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/corporate-travel
server:
  port: 8082
opa:
  url: http://opa:8181
```

### Environment Profiles
- **default** — connects to `localhost` PostgreSQL
- **docker** — connects to Docker Compose service names

## Database Schema

Managed by Flyway:

- **V1** — `expense.expenses` + `expense.expense_items` tables
- **V2** — `expense.expense_audit` table (`actor_id`, `subject_id`, `delegation_id`, `consent_id`, `tenant_id`, `action`, `timestamp`, `details`)

See [FLYWAY-MIGRATION.md](./FLYWAY-MIGRATION.md) for migration procedures.

## Running the Service

### Local Development

```bash
./gradlew :services:expense-service:build
./gradlew :services:expense-service:bootRun
# Available at http://localhost:8082
```

### Docker Compose

```bash
docker-compose up -d
docker-compose logs -f expense-service
```

## Security

### Authentication
- OAuth 2.0 JWT tokens from Keycloak
- `realm_access.roles` required in token (via `oidc-usermodel-realm-role-mapper` on `user-attributes` scope)

### Authorization (via OPA)
- **Employee**: Can manage own expenses
- **Manager**: Can approve/reject team expenses
- **Admin**: Full access
- **Delegation**: Assistants can manage executive expenses when `book_travel` consent is active

### Delegation Headers
Mutation endpoints accept:
- `X-Delegated-Subject` — human principal the expense belongs to
- `X-Delegation-Id` — the delegation record UUID
- `X-Consent-Id` — the consent record UUID

## Testing

```bash
# Unit tests
./gradlew :services:expense-service:test

# Get access token
TOKEN=$(curl -s -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d "client_id=employee-portal&username=alice.employee&password=password123&grant_type=password" \
  | jq -r '.access_token')

# Create expense
curl -X POST http://localhost:8082/api/expenses \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"NYC Trip","description":"Client meeting expenses"}'

# Submit for approval
curl -X POST http://localhost:8082/api/expenses/{id}/submit \
  -H "Authorization: Bearer $TOKEN"

# Approve (as manager)
MGMT_TOKEN=$(curl -s -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d "client_id=employee-portal&username=bob.manager&password=password123&grant_type=password" \
  | jq -r '.access_token')
curl -X POST http://localhost:8082/api/expenses/{id}/approve \
  -H "Authorization: Bearer $MGMT_TOKEN"
```

## API Documentation

- Swagger UI: http://localhost:8082/swagger-ui.html
- OpenAPI spec: http://localhost:8082/api-docs

## Architecture

```
ExpenseController (REST — extracts SecurityContext with delegation headers)
    ↓
ExpenseServiceImpl (OPA authorize → business logic → audit write, same @Transactional)
    ↓
ExpenseRepository / ExpenseItemRepository     ExpenseAuditRepository
    ↓                                               ↓
PostgreSQL expense.expenses                 PostgreSQL expense.expense_audit
         expense.expense_items
```

## Project Structure

```
expense-service/
├── src/main/java/com/corporate/travel/expense/
│   ├── ExpenseServiceApplication.java
│   ├── config/OpenApiConfig.java
│   ├── controller/ExpenseController.java
│   ├── exception/
│   │   ├── ExpenseNotFoundException.java
│   │   ├── ExpenseItemNotFoundException.java
│   │   ├── InvalidExpenseStatusException.java
│   │   └── GlobalExceptionHandler.java
│   ├── model/entity/
│   │   ├── Expense.java
│   │   ├── ExpenseItem.java
│   │   └── ExpenseAudit.java
│   ├── repository/
│   │   ├── ExpenseRepository.java
│   │   ├── ExpenseItemRepository.java
│   │   └── ExpenseAuditRepository.java
│   └── service/
│       ├── ExpenseService.java
│       ├── ExpenseAuditService.java
│       └── impl/
│           ├── ExpenseServiceImpl.java
│           └── ExpenseAuditServiceImpl.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-docker.yml
│   └── db/migration/
│       ├── V1__baseline_expenses_schema.sql
│       └── V2__add_expense_audit.sql
├── build.gradle
├── Dockerfile
├── FLYWAY-MIGRATION.md
└── README.md
```

## Health Check

```bash
curl http://localhost:8082/actuator/health
```

## Troubleshooting

**Service won't start**
- Check PostgreSQL: `docker-compose ps postgres`
- Check Keycloak: `docker-compose ps keycloak`
- Check logs: `docker-compose logs expense-service`

**403 on expense creation**
- Verify `realm_access.roles` is in the JWT (requires `oidc-usermodel-realm-role-mapper` on `user-attributes` scope in Keycloak)
- Test: decode token at jwt.io and check for `realm_access.roles`

**Migration failed**
- See [FLYWAY-MIGRATION.md](./FLYWAY-MIGRATION.md) troubleshooting section

## Related Services

- **Employee BFF** (8085) — injects delegation headers for delegated expense operations
- **Delegation Service** (8083) — delegation records referenced by audit
- **Travel Service** (8081) — bookings that expenses can reference
- **API Gateway** (8000) — routes `/api/expenses/**` to this service

## Related Documentation

- [DELEGATION-FLOW.md](../../DELEGATION-FLOW.md)
- [FLYWAY-MIGRATION.md](./FLYWAY-MIGRATION.md)
- [ADR-011: Audit and Compliance Ledger](../../architecture-decision-records/ADR-011:%20Implement%20Comprehensive%20Audit%20and%20Compliance%20Ledger.md)
- [ADR-023: Flyway Migrations](../../architecture-decision-records/ADR-023:%20Adopt%20Flyway%20for%20Database%20Schema%20Migration%20Management.md)
