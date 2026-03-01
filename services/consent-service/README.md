## Consent Service

Manages consent records with purpose binding for delegated access in the Corporate Travel & Expense platform.

### Features

- ✅ Consent lifecycle management (grant, revoke, expire)
- ✅ Purpose binding and scope validation
- ✅ Multi-tenant isolation
- ✅ OPA-based authorization
- ✅ Complete audit trail
- ✅ Link to delegation records
- ✅ RESTful API with OpenAPI documentation

### API Endpoints

```
POST   /api/consents                 - Grant consent
GET    /api/consents                 - List my consents  
GET    /api/consents/{id}            - Get consent details
DELETE /api/consents/{id}            - Revoke consent
POST   /api/consents/validate        - Validate consent
GET    /api/consents/{id}/audit      - Get audit trail
GET    /api/consents/my-consents     - Consents I granted
GET    /api/consents/to-me           - Consents granted to me
```

### Configuration

**application.yml** (Local):
```yaml
server:
  port: 8084

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/corporate_travel
    username: admin
    password: admin123
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: consent
```

**application-docker.yml** (Docker):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/corporate_travel
```

### Running Locally

```bash
# Compile
./gradlew :services:consent-service:build

# Run
./gradlew :services:consent-service:bootRun

# Test
./gradlew :services:consent-service:test
```

### Running with Docker

```bash
# Build image
docker build -t consent-service -f services/consent-service/Dockerfile .

# Run container
docker run -p 8084:8084 consent-service
```

### OpenAPI Documentation

Once running, access Swagger UI at:
- http://localhost:8084/swagger-ui.html

### Database Schema

- **Schema**: `consent`
- **Tables**:
  - `consents` - Consent records with purpose binding
  - `consent_audit` - Audit trail for all consent events

### Integration with Other Services

- **Delegation Service**: Links consents to delegation records via `delegation_id`
- **OPA**: Authorizes all consent operations
- **Keycloak**: Validates JWT tokens for authentication

### Example Usage

**Grant Consent**:
```bash
curl -X POST http://localhost:8084/api/consents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "grantorId": "carol.executive",
    "granteeId": "dave.assistant",
    "purpose": "book_travel",
    "scopes": ["view_bookings", "create_bookings"],
    "dataCategories": ["travel_data"]
  }'
```

**Validate Consent**:
```bash
curl -X POST http://localhost:8084/api/consents/validate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "grantorId": "carol.executive",
    "granteeId": "dave.assistant",
    "purpose": "book_travel",
    "scopes": ["create_bookings"]
  }'
```

### Architecture

Following microservices patterns established in the platform:
- **Spring Boot 3.2.2** with Java 17
- **PostgreSQL** for consent storage
- **Flyway** for database migrations
- **OPA** for authorization
- **JWT** authentication via Keycloak
- **OpenAPI 3.0** documentation

### Testing

Unit tests follow established patterns:
- **JUnit 5** + **Mockito** + **AssertJ**
- **Target**: 80%+ line coverage, 100% branch coverage
- **Patterns**: AAA (Given-When-Then), test builders, security mocking