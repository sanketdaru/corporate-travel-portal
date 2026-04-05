# Implementation Guide

This document provides a reference guide for the Corporate Travel & Expense platform implementation patterns and architecture.

## Current Implementation Status

**Last updated**: 2026-04-04 — Phase 4 Complete, MVP Done (71/71 E2E tests passing)

### Completed

- [x] Monorepo structure with Gradle multi-project build
- [x] Docker Compose infrastructure (Keycloak, PostgreSQL, Neo4j, OPA)
- [x] Keycloak realm configuration — 5 users, `realm_access.roles` via `oidc-usermodel-realm-role-mapper`
- [x] OPA authorization policies (Rego) — RBAC, tenant isolation, delegation-aware rules
- [x] Shared security library — `SecurityContext`, `JwtAuthenticationConverter` (with delegation header overload), `OpaClient`
- [x] Shared domain models — `BookingStatus`, `ExpenseStatus`, `ExpenseCategory` enums
- [x] Travel Service — bookings CRUD, OPA, audit trail (CREATE/STATUS_CHANGE/DELETE), delegation headers
- [x] Expense Service — expense + items, workflow, OPA, audit trail (all mutations), delegation headers
- [x] API Gateway — Spring Cloud Gateway with JWT validation and routing
- [x] Delegation Service — JPA + Neo4j dual-write, graph chain traversal, unit tests
- [x] Consent Service — grant/validate/revoke, purpose binding, scheduler, audit trail, unit tests
- [x] Employee BFF — Standard Token Exchange V2 (RFC 8693), delegation context (session), API aggregation
- [x] Audit service layer — `BookingAuditService`, `ExpenseAuditService`, `booking_audit` + `expense_audit` tables
- [x] Flyway migrations — V1 baseline + V2 audit tables in travel-service and expense-service
- [x] End-to-end delegation regression script — 71 assertions across 10 phases

### Deferred (Post-MVP)

- [ ] Frontend (Next.js employee portal)
- [ ] Approval Service
- [ ] Keycloak SPI for token enrichment
- [ ] OpenTelemetry distributed tracing
- [ ] HashiCorp Vault
- [ ] Kubernetes deployment

## Implementation Patterns

### Service Structure

Each microservice follows this standard layout:

```
services/{service-name}/
├── build.gradle
├── Dockerfile
├── src/
│   ├── main/
│   │   ├── java/com/corporate/travel/{service}/
│   │   │   ├── {Service}Application.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── OpenApiConfig.java
│   │   │   ├── controller/
│   │   │   │   └── {Entity}Controller.java
│   │   │   ├── service/
│   │   │   │   ├── {Entity}Service.java
│   │   │   │   └── impl/{Entity}ServiceImpl.java
│   │   │   ├── repository/
│   │   │   │   └── {Entity}Repository.java
│   │   │   ├── model/
│   │   │   │   ├── entity/{Entity}.java
│   │   │   │   └── dto/{Entity}DTO.java
│   │   │   └── exception/
│   │   │       └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-docker.yml
│   │       └── db/migration/
│   │           └── V1__{service}_schema.sql
│   └── test/
│       └── java/com/corporate/travel/{service}/
└── README.md
```

### Standard Dependencies

```gradle
plugins {
    id 'org.springframework.boot'
    id 'java'
}

dependencies {
    implementation project(':services:shared:security-commons')
    implementation project(':services:shared:domain-models')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    runtimeOnly 'org.postgresql:postgresql'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}
```

### Application Configuration Template

```yaml
spring:
  application:
    name: {service-name}

  datasource:
    url: jdbc:postgresql://localhost:5432/corporate_travel
    username: admin
    password: admin123

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: {schema-name}

  flyway:
    enabled: true
    schemas: {schema-name}

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/corporate-travel

server:
  port: {port}

opa:
  url: http://localhost:8181

logging:
  level:
    com.corporate.travel: DEBUG
    org.springframework.security: DEBUG
```

## OPA Authorization Pattern

Every service operation calls OPA before performing business logic:

```java
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository repository;
    private final BookingAuditService auditService;
    private final OpaClient opaClient;

    @Override
    @Transactional
    public Booking createBooking(Booking booking, SecurityContext context) {
        Map<String, Object> resource = Map.of(
            "type", "booking",
            "tenant_id", context.getTenantId()
        );

        if (!opaClient.authorize(context, "create_booking", resource)) {
            throw new AccessDeniedException("Not authorized to create booking");
        }

        booking.setTenantId(context.getTenantId());
        booking.setUserId(context.getSubjectId());   // subject (may be delegated-to user)
        booking.setCreatedBy(context.getUserId());   // actor (person making the call)
        Booking saved = repository.save(booking);

        auditService.recordAction(saved.getId(), "CREATE", context);
        return saved;
    }
}
```

### SecurityContext — Delegation-Aware

```java
// Direct call (Alice books for herself)
SecurityContext {
    userId     = "alice.employee"  // actor == subject
    subjectId  = "alice.employee"
    tenantId   = "tenant-a"
    roles      = ["employee"]
    delegationId = null
}

// Delegated call (Dave books for Carol via BFF)
SecurityContext {
    userId     = "dave.assistant"   // actor — token sub
    subjectId  = "carol.executive"  // from X-Delegated-Subject header
    tenantId   = "tenant-a"
    roles      = ["assistant"]
    delegationId = "uuid-of-delegation"  // from X-Delegation-Id header
}
```

The header-aware `JwtAuthenticationConverter` overload is used on mutation endpoints:

```java
@PostMapping
public ResponseEntity<Booking> createBooking(
        @RequestBody BookingRequest req,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest httpRequest) {

    SecurityContext ctx = JwtAuthenticationConverter.extractSecurityContext(jwt, httpRequest);
    return ResponseEntity.ok(bookingService.createBooking(req, ctx));
}
```

## Audit Trail Pattern

Every service that mutates data has an audit service layer. The audit write shares the same transaction as the business write (same `@Transactional` scope — `REQUIRED`, not `REQUIRES_NEW`) to satisfy the FK constraint `booking_audit.booking_id → bookings.id`.

```java
// V2__add_booking_audit.sql (Flyway migration)
CREATE TABLE travel.booking_audit (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id    UUID NOT NULL REFERENCES travel.bookings(id),
    action        VARCHAR(100) NOT NULL,  -- CREATE, STATUS_CHANGE, DELETE
    actor_id      VARCHAR(255) NOT NULL,
    subject_id    VARCHAR(255) NOT NULL,
    delegation_id UUID,
    consent_id    UUID,
    tenant_id     VARCHAR(255) NOT NULL,
    timestamp     TIMESTAMP NOT NULL DEFAULT NOW(),
    details       JSONB
);
```

Query audit trail:
```bash
GET /api/bookings/{id}/audit
GET /api/expenses/{id}/audit
```

## Token Exchange Pattern (BFF)

The BFF performs Standard Token Exchange V2 per RFC 8693:

```
POST /realms/corporate-travel/protocol/openid-connect/token
  grant_type        = urn:ietf:params:oauth:grant-type:token-exchange
  subject_token     = <Dave's JWT>
  subject_token_type = urn:ietf:params:oauth:token-type:access_token
  requested_token_type = urn:ietf:params:oauth:token-type:access_token
  audience          = travel-service
  requested_subject = carol.executive
```

Prerequisites in Keycloak:
- `KC_FEATURES=token-exchange-standard` on the Keycloak container
- Standard token exchange enabled on `employee-bff` client
- `aud` mapper on `employee-bff` scope mapping to `travel-service`
- `preferred_username` mapper on `user-attributes` scope

## Building a New Service: Checklist

1. Create directory structure under `services/{name}/`
2. Add to `settings.gradle`: `include ':services:{name}'`
3. Write `build.gradle` following the standard template above
4. Create `V1__{name}_schema.sql` Flyway migration with `CREATE SCHEMA` + tables
5. Implement entity → repository → service/impl → controller
6. Add OPA authorize call in service layer before each operation
7. Add audit service layer and wire into `ServiceImpl`
8. Write unit tests (`@ExtendWith(MockitoExtension.class)`)
9. Add service to `docker-compose.yml`
10. Write service `README.md`

## Troubleshooting

**403 on booking/expense creation**
- Verify `oidc-usermodel-realm-role-mapper` is on the `user-attributes` client scope in Keycloak
- Without this, `realm_access.roles` is absent from tokens and `has_role("employee")` fails in OPA

**OPA policy changes not applying**
- OPA requires `--watch` flag for hot-reload; already set in `docker-compose.yml`
- If OPA was started without `--watch`: push policy via REST (`PUT /v1/policies/corporate/travel`) or `docker-compose restart opa`

**Token exchange fails (400 Bad Request)**
- Verify `KC_FEATURES=token-exchange-standard` in `docker-compose.yml` (not `token-exchange` which is the deprecated preview)
- Verify standard token exchange is toggled on the `employee-bff` Keycloak client

**Delegation context not appearing in audit**
- Confirm BFF is injecting `X-Delegated-Subject`, `X-Delegation-Id`, `X-Consent-Id` headers
- Confirm downstream controller is calling the `extractSecurityContext(jwt, httpRequest)` overload (not the single-arg version)

**Neo4j connection issues**
```bash
docker-compose exec neo4j cypher-shell -u neo4j -p password123
```

**JWT validation fails**
- Verify `issuer-uri` in `application-docker.yml` uses the Docker service name (`keycloak`), not `localhost`

## Additional Resources

- [OAuth 2.0 Token Exchange RFC 8693](https://datatracker.ietf.org/doc/html/rfc8693)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Open Policy Agent Documentation](https://www.openpolicyagent.org/docs/latest/)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Project ADRs](./architecture-decision-records/)
- [Delegation Flow Guide](./DELEGATION-FLOW.md)
