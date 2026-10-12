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
  flyway:
    schemas: consent
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/corporate-travel
          audiences: [consent-service]   # rejects tokens not issued for this service
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

### Running in Compose

```bash
podman compose up -d --build consent-service
podman compose logs -f consent-service
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
- **Keycloak**: Issues the JWTs; `aud` must contain `consent-service`
- **Employee BFF**: Validates consent during delegation activation and records the `consentId` for audit

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
- **Spring Boot 4.1.1** with Java 25
- **PostgreSQL** for consent storage
- **Flyway** for database migrations
- **OPA** for authorization
- **JWT** authentication via Keycloak
- **OpenAPI 3.0** documentation

### Testing

Unit tests follow established patterns:
- **JUnit 6** + **Mockito** + **AssertJ**
- **Target**: 80%+ line coverage, 100% branch coverage
- **Patterns**: AAA (Given-When-Then), test builders, security mocking