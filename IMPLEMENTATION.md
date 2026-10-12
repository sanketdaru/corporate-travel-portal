# Implementation Guide

This document provides a reference guide for the Corporate Travel & Expense platform implementation patterns and architecture.

## Current Implementation Status

**Last updated**: 2026-10-08 — platform upgrade complete (see [docs/UPGRADE-PLAN.md](./docs/UPGRADE-PLAN.md)); E2E 73/73

### Platform

| Area | Version |
|------|---------|
| Java / Gradle | 25 (toolchain via foojay resolver) / 9.8.1 |
| Spring Boot / Cloud | 4.1.1 / 2025.1.3 (Security 7.1, Jackson 3, Hibernate 7, Flyway 12, Spring Data Neo4j 8) |
| Runtime image | `eclipse-temurin:25-jre-noble` |
| Keycloak | 26.8.0, features `token-exchange-delegation,parameterized-scopes` (preview) |
| PostgreSQL / Neo4j / OPA | 18.6 / 2026.09.0-community / 1.21.1 |
| Frontend | Next 16.4, React 19.3, next-auth 5 beta.32, TypeScript 6, Tailwind 4.3, Node >= 22 |

### Completed

- [x] Monorepo structure with Gradle multi-project build
- [x] Compose infrastructure (Keycloak, PostgreSQL, Neo4j, OPA), run with `podman compose` or `docker compose`
- [x] Keycloak realm-as-code — `infrastructure/keycloak/config/corporate-travel.json` applied by the `keycloak-config` one-shot (keycloak-config-cli) on every `up`; FGAP v2 delegation permissions applied by the `keycloak-fgap` one-shot
- [x] OPA authorization policies (Rego v1) — RBAC, tenant isolation, delegation-aware rules
- [x] Shared security library — `SecurityContext`, `JwtAuthenticationConverter`, `DelegatedActorFilter`, `OpaClient`, audience validation in every resource server
- [x] Shared domain models — `BookingStatus`, `ExpenseStatus`, `ExpenseCategory` enums
- [x] Travel Service — travel authorizations (bookings) CRUD, OPA, audit trail (CREATE/STATUS_CHANGE/DELETE), RFC 8693 delegated tokens
- [x] Expense Service — expense + items, workflow, OPA, audit trail (all mutations), RFC 8693 delegated tokens
- [x] API Gateway — Spring Cloud Gateway with JWT and audience validation and routing
- [x] Delegation Service — JPA + Neo4j dual-write, graph chain traversal, unit tests
- [x] Consent Service — grant/validate/revoke, purpose binding, scheduler, audit trail, unit tests
- [x] Employee BFF — Keycloak RFC 8693 delegation (ADR-024): delegator grant ceremony, encrypted grant store, `actor_token` exchange per audience, delegation context (session), API aggregation
- [x] Audit service layer — `BookingAuditService`, `ExpenseAuditService`, `booking_audit` + `expense_audit` tables
- [x] Flyway migrations — travel-service V1–V4, expense-service V1–V2, BFF grant store (schema `bff`)
- [x] Frontend (Next.js employee portal)
- [x] End-to-end delegation regression script — 73 checks
- [x] CI (`.github/workflows/ci.yml`) — backend build/test on JDK 25, frontend lint/build on Node 24, realm validation, script syntax

### Deferred (Post-MVP)

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

    // Boot 4 modular starters
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1'

    runtimeOnly 'org.postgresql:postgresql'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    implementation 'org.flywaydb:flyway-database-postgresql'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
}
```

Versions come from the Spring Boot 4.1.1 and Spring Cloud 2025.1.3 BOMs in the root `build.gradle`. Jackson 3 uses the `tools.jackson.*` packages (annotations stay in `com.fasterxml.jackson.annotation`).

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
          # Must match the token's iss claim (Keycloak runs with KC_HOSTNAME=localhost)
          issuer-uri: http://localhost:8080/realms/corporate-travel
          # In containers: http://keycloak:8080/realms/corporate-travel/protocol/openid-connect/certs
          jwk-set-uri: http://localhost:8080/realms/corporate-travel/protocol/openid-connect/certs
          # Reject tokens whose aud does not contain this service's client ID
          audiences:
            - {service-name}

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
        booking.setUserId(context.getSubjectId());   // subject (delegator when delegated)
        booking.setCreatedBy(context.getUserId());   // actor (person making the call)
        Booking saved = repository.save(booking);

        auditService.record(saved.getId(), "CREATE", Map.of("destination", saved.getDestination()), context);
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

// Delegated call (Dave books for Carol via BFF, ADR-024)
// Token: sub = Carol, act.sub = Dave; X-Actor-Token = Dave's own JWT
SecurityContext {
    userId     = "dave.assistant"   // actor — from the verified X-Actor-Token
    subjectId  = "carol.executive"  // delegator — from the delegated token itself
    tenantId   = "tenant-a"
    roles      = ["assistant"]      // the actor's roles, never the delegator's
    delegationId = "uuid-of-delegation"  // X-Delegation-Id header, audit metadata only
}
```

A request is delegated only if its token carries an RFC 8693 `act` claim. `DelegatedActorFilter` (registered by `security-commons`) then verifies `X-Actor-Token`: valid signature and audience, `sub` equal to `act.sub`, same tenant, not itself delegated. Otherwise it returns 401 `invalid_delegation`. `X-Delegation-Id`, `X-Consent-Id` and `X-Delegation-Purpose` are audit metadata; headers alone never make a request delegated. Previously the subject came from an `X-Delegated-Subject` header, which is now ignored.

Controllers use the request-aware `JwtAuthenticationConverter` overload:

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
// travel.booking_audit after V1 (table) + V2 (delegation_id, consent_id, tenant_id)
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

The BFF uses Keycloak's RFC 8693 delegation (ADR-024, preview in Keycloak 26.8):

1. **Grant (delegator, once):** Carol signs in through the `delegation-grant` client with `offline_access` and `delegation:user:<delegate>` and accepts the consent screen. The BFF stores her offline token AES-256-GCM encrypted in Postgres (schema `bff`).
2. **Activate (delegate):** the BFF refreshes Carol's token (it carries `may_act` for Dave) and exchanges it with Dave's token as `actor_token`, once per downstream audience:

```
POST /realms/corporate-travel/protocol/openid-connect/token
  grant_type           = urn:ietf:params:oauth:grant-type:token-exchange
  subject_token        = <Carol's access token, carrying may_act>
  subject_token_type   = urn:ietf:params:oauth:token-type:access_token
  actor_token          = <Dave's access token>
  actor_token_type     = urn:ietf:params:oauth:token-type:access_token
  requested_token_type = urn:ietf:params:oauth:token-type:access_token
  audience             = travel-service        # repeated for expense-service
```

The issued token has `sub` = Carol and `act.sub` = Dave. Tokens stay in the BFF session and are never returned to the client. The exchange uses Spring Security's `RestClientTokenExchangeTokenResponseClient`, called statelessly.

Prerequisites in Keycloak (all in realm-as-code or compose):
- `KC_FEATURES: token-exchange-delegation,parameterized-scopes` on the Keycloak container
- Standard token exchange enabled on `employee-bff` only
- `delegation-grant` client and `delegation:user` client scope
- FGAP v2 delegation permissions, applied by the `keycloak-fgap` one-shot (`infrastructure/keycloak/fgap/`)
- Audience mappers for the four backend services on `employee-portal` and `employee-bff`

## Building a New Service: Checklist

1. Create directory structure under `services/{name}/`
2. Add to `settings.gradle`: `include ':services:{name}'`
3. Write `build.gradle` following the standard template above
4. Create `V1__{name}_schema.sql` Flyway migration with `CREATE SCHEMA` + tables
5. Implement entity → repository → service/impl → controller
6. Add OPA authorize call in service layer before each operation
7. Add audit service layer and wire into `ServiceImpl`
8. Write unit tests (`@ExtendWith(MockitoExtension.class)`)
9. Add service to `docker-compose.yml` (depend on `keycloak-config` with `service_completed_successfully`)
10. Add a Keycloak client and an audience mapper to `infrastructure/keycloak/config/corporate-travel.json`
11. Write service `README.md`

## Troubleshooting

**403 on booking/expense creation**
- Verify `realm_access.roles` is in the token. It comes from the standard `roles` client scope, which must be a default scope of the requesting client.

**401 with a valid-looking token**
- Check `aud`: each service accepts only tokens audienced for itself (the gateway accepts any of the four backend audiences)
- `invalid_delegation`: a delegated token arrived without a valid `X-Actor-Token`

**OPA policy changes not applying**
- OPA requires `--watch` flag for hot-reload; already set in `docker-compose.yml`
- If OPA was started without `--watch`: push policy via REST (`PUT /v1/policies/corporate/travel`) or `podman compose restart opa`

**Delegation activation fails**
- 409: the delegator has not authorized the delegation in Keycloak yet (`POST /api/bff/delegation/{id}/grant`)
- 400 from Keycloak: verify `KC_FEATURES` includes `token-exchange-delegation,parameterized-scopes` and that `keycloak-fgap` exited with 0 (`podman compose ps -a`)

**Delegation context not appearing in audit**
- Confirm the BFF sends the delegated token plus `X-Actor-Token` and `X-Delegation-Id`
- Confirm the downstream controller calls the `extractSecurityContext(jwt, httpRequest)` overload (not the single-arg version)

**Neo4j connection issues**
```bash
podman compose exec neo4j cypher-shell -u neo4j -p password123
```

**JWT validation fails**
- `issuer-uri` must be `http://localhost:8080/realms/corporate-travel` (the token's `iss`), also inside containers
- `jwk-set-uri` uses the compose service name (`keycloak:8080`) inside containers

## Additional Resources

- [OAuth 2.0 Token Exchange RFC 8693](https://datatracker.ietf.org/doc/html/rfc8693)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Open Policy Agent Documentation](https://www.openpolicyagent.org/docs/latest/)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Project ADRs](./architecture-decision-records/)
- [ADR-024: Keycloak Native RFC 8693 Delegation](./architecture-decision-records/ADR-024:%20Adopt%20Keycloak%20Native%20RFC%208693%20Delegation.md)
- [Delegate Journey](./docs/journeys/DELEGATE-JOURNEY.md)
