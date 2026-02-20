# Expense Service

A Spring Boot microservice for managing employee expense reports in the Corporate Travel & Expense Portal.

## Features

- **Expense Report Management**: Create, read, update, and delete expense reports
- **Line Item Support**: Add multiple expense items (meals, travel, accommodation, etc.)
- **Approval Workflow**: Submit for approval, approve/reject by managers, mark as paid
- **Multi-tenant Isolation**: Complete tenant data separation
- **Delegation Support**: Assistants can manage expenses on behalf of executives
- **OPA Authorization**: Fine-grained access control through Open Policy Agent
- **Booking Integration**: Link expenses to travel bookings
- **Database Migration**: Flyway for version-controlled schema management
- **API Documentation**: OpenAPI/Swagger UI available

## Architecture

### Database Schema (Flyway Managed)

The service manages its own `expense` schema with three tables:

1. **expenses** - Main expense reports
2. **expense_items** - Line items for expenses
3. **expense_audit** - Audit trail for compliance

See [FLYWAY-MIGRATION.md](./FLYWAY-MIGRATION.md) for complete migration documentation.

### Workflow States

```
DRAFT → SUBMITTED → APPROVED/REJECTED → PAID
```

- **DRAFT**: Being created/edited by employee
- **SUBMITTED**: Submitted for manager approval
- **APPROVED**: Approved by manager (ready for reimbursement)
- **REJECTED**: Rejected by manager
- **PAID**: Reimbursement processed

## API Endpoints

### Expense Management
- `POST /api/expenses` - Create new expense report
- `GET /api/expenses` - List all expenses (tenant-filtered)
- `GET /api/expenses/{id}` - Get specific expense
- `PUT /api/expenses/{id}` - Update expense details
- `DELETE /api/expenses/{id}` - Delete expense (soft delete)

### Expense Items
- `POST /api/expenses/{expenseId}/items` - Add expense item
- `PUT /api/expenses/{expenseId}/items/{itemId}` - Update expense item
- `DELETE /api/expenses/{expenseId}/items/{itemId}` - Remove expense item

### Workflow Operations
- `POST /api/expenses/{id}/submit` - Submit for approval
- `POST /api/expenses/{id}/approve` - Approve expense (managers only)
- `POST /api/expenses/{id}/reject` - Reject expense (managers only)
- `POST /api/expenses/{id}/pay` - Mark as paid (finance only)

### Documentation
- `GET /swagger-ui.html` - Interactive API documentation
- `GET /api-docs` - OpenAPI specification (JSON)

## Configuration

### Application Properties

```yaml
# Database
spring.datasource.url: jdbc:postgresql://localhost:5432/corporate_travel
spring.datasource.username: admin
spring.datasource.password: admin123

# Flyway Migration
spring.flyway.enabled: true
spring.flyway.schemas: expense

# JPA (Flyway is source of truth)
spring.jpa.hibernate.ddl-auto: validate
spring.jpa.properties.hibernate.default_schema: expense

# OAuth 2.0 Resource Server
spring.security.oauth2.resourceserver.jwt.issuer-uri: http://keycloak:8080/realms/corporate-travel

# OPA Authorization
opa.url: http://opa:8181
```

### Environment Profiles

- **default** (local): Connects to localhost PostgreSQL
- **docker**: Connects to Docker Compose services

## Running the Service

### Prerequisites
- Java 17+
- PostgreSQL 15+ with `corporate_travel` database
- Keycloak running on port 8080
- OPA running on port 8181

### Local Development

```bash
# Build the service
./gradlew :services:expense-service:build

# Run the service
./gradlew :services:expense-service:bootRun

# Service will be available at http://localhost:8082
```

### Docker Compose

```bash
# Start all infrastructure and services
docker-compose up -d

# View logs
docker-compose logs -f expense-service

# Stop services
docker-compose down
```

### Database Migration

On startup, Flyway will automatically:
1. Create the `expense` schema if it doesn't exist
2. Apply all pending migrations from `src/main/resources/db/migration/`
3. Track migration history in `flyway_schema_history` table

See [FLYWAY-MIGRATION.md](./FLYWAY-MIGRATION.md) for detailed migration procedures.

## Security

### Authentication
- OAuth 2.0 JWT tokens from Keycloak
- Token validation on every request
- Claims extracted: user_id, tenant_id, roles, delegation info

### Authorization (via OPA)
- **Employee**: Can manage own expenses
- **Manager**: Can approve team expenses
- **Finance**: Can mark expenses as paid
- **Admin**: Full access to all expenses
- **Delegation**: Assistants can manage executive expenses

### Multi-tenant Isolation
Every query is filtered by `tenant_id` - no cross-tenant access possible.

## Testing

### Get Access Token

```bash
# Get token for alice.employee (TenantA)
./scripts/get-token.sh alice.employee password123

# Store token
export TOKEN="<jwt-token>"
```

### Create Expense Report

```bash
curl -X POST http://localhost:8082/api/expenses \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Business Trip to Mumbai",
    "description": "Client meeting expenses",
    "bookingId": "uuid-of-booking"
  }'
```

### Add Expense Item

```bash
curl -X POST http://localhost:8082/api/expenses/{expenseId}/items \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "date": "2026-02-15",
    "category": "MEALS",
    "description": "Client dinner at Taj Hotel",
    "amount": 5500.00,
    "currency": "INR",
    "receiptUrl": "https://storage.example.com/receipts/123.jpg"
  }'
```

### Submit for Approval

```bash
curl -X POST http://localhost:8082/api/expenses/{expenseId}/submit \
  -H "Authorization: Bearer $TOKEN"
```

### Approve Expense (as manager)

```bash
# Get manager token
./scripts/get-token.sh bob.manager password123
export MANAGER_TOKEN="<jwt-token>"

# Approve
curl -X POST http://localhost:8082/api/expenses/{expenseId}/approve \
  -H "Authorization: Bearer $MANAGER_TOKEN"
```

## Development

### Project Structure

```
expense-service/
├── src/main/java/com/corporate/travel/expense/
│   ├── ExpenseServiceApplication.java
│   ├── config/
│   │   └── OpenApiConfig.java
│   ├── controller/
│   │   └── ExpenseController.java
│   ├── exception/
│   │   ├── ExpenseNotFoundException.java
│   │   ├── ExpenseItemNotFoundException.java
│   │   ├── InvalidExpenseStatusException.java
│   │   └── GlobalExceptionHandler.java
│   ├── model/entity/
│   │   ├── Expense.java
│   │   └── ExpenseItem.java
│   ├── repository/
│   │   ├── ExpenseRepository.java
│   │   └── ExpenseItemRepository.java
│   └── service/
│       ├── ExpenseService.java
│       └── impl/ExpenseServiceImpl.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-docker.yml
│   └── db/migration/
│       └── V1__baseline_expenses_schema.sql
├── build.gradle
├── Dockerfile
├── FLYWAY-MIGRATION.md
└── README.md (this file)
```

### Adding New Migrations

1. Create new migration file: `V2__add_new_feature.sql`
2. Write SQL changes
3. Restart service - Flyway applies automatically
4. Commit migration file to version control

See [FLYWAY-MIGRATION.md](./FLYWAY-MIGRATION.md) for detailed guidance.

### Shared Libraries

The service uses two shared libraries:

1. **security-commons**: JWT processing, OPA integration, SecurityContext
2. **domain-models**: Shared enums (ExpenseStatus, ExpenseCategory)

## API Documentation

Once running, access:
- **Swagger UI**: http://localhost:8082/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8082/api-docs

## Related Services

- **Travel Service**: Manages travel bookings (can be referenced in expenses)
- **Approval Service**: Manages multi-step approval workflows
- **Keycloak**: Identity and access management
- **OPA**: Authorization policy engine

## Related Documentation

- [ADR-023: Adopt Flyway for Database Schema Migration](../../architecture-decision-records/ADR-023:%20Adopt%20Flyway%20for%20Database%20Schema%20Migration%20Management.md)
- [Flyway Migration Guide](./FLYWAY-MIGRATION.md)
- [Main Project README](../../README.md)
- [Getting Started Guide](../../GETTING-STARTED.md)

## Health Checks

```bash
# Health endpoint
curl http://localhost:8082/actuator/health

# Metrics
curl http://localhost:8082/actuator/metrics
```

## Troubleshooting

### Service won't start
- Check PostgreSQL is running: `docker-compose ps postgres`
- Check Keycloak is running: `docker-compose ps keycloak`
- Check logs: `docker-compose logs expense-service`

### Migration failed
- See [FLYWAY-MIGRATION.md](./FLYWAY-MIGRATION.md) troubleshooting section
- Drop schema and restart for clean slate

### Authorization errors
- Verify token is valid: Check expiry and issuer
- Check OPA is running: `curl http://localhost:8181/health`
- Verify user has correct role in Keycloak

## Future Enhancements

- [ ] Implement audit logging in service layer
- [ ] Add expense report templates
- [ ] Automated expense policy validation
- [ ] Receipt OCR integration
- [ ] Currency conversion support
- [ ] Expense analytics and reporting
- [ ] Email notifications for approvals
- [ ] Integration tests with test containers

---

**Service Port**: 8082  
**Database Schema**: expense  
**Spring Boot Version**: 3.2.2  
**Java Version**: 17  
**Build Tool**: Gradle 8.5