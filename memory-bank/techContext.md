# Technical Context

**Last Updated**: 2026-10-08 (after upgrade phases 0–7, see `docs/UPGRADE-PLAN.md`)

## Technology Stack

### Backend Services
- **Framework**: Spring Boot 4.1.1 (Framework 7, Security 7.1, Jackson 3, Hibernate 7), Spring Cloud 2025.1.3 (Gateway 5.0)
- **Language**: Java 25 (Gradle toolchain; foojay resolver downloads it if missing)
- **Build Tool**: Gradle 9.8.1 wrapper (multi-project build)
- **Security**: Spring Security OAuth2 Resource Server with `aud` validation in every service
- **ORM**: Spring Data JPA with Hibernate 7; Spring Data Neo4j 8.1 (driver 6.1) in delegation-service
- **Migrations**: Flyway 12.4 (Boot BOM), one schema per service
- **API docs**: springdoc 3.1.1

### Frontend (`frontend/nextjs`)
- **Framework**: Next.js 16.4, React 19.3
- **Language**: TypeScript 6.0
- **Design System**: shadcn/ui, Tailwind CSS 4.3
- **Auth**: next-auth 5.0.0-beta.32 (`@auth/core` 0.41.3), Keycloak PKCE on the public `employee-portal` client
- **Lint**: ESLint 9 (eslint-config-next 16.4 peers stop at ESLint 9)
- **Node**: `engines.node >=22`; CI uses Node 24

### Infrastructure (all images pinned)
- **IAM**: Keycloak 26.8.0, `start-dev`, preview features `token-exchange-delegation,parameterized-scopes`
- **Realm-as-code**: `adorsys/keycloak-config-cli:6.5.1-26.5.5` one-shot (`keycloak-config`) applies `infrastructure/keycloak/config/corporate-travel.json`; `keycloak-fgap` one-shot (`kcadm.sh`) applies FGAP v2 delegation permissions
- **Relational DB**: PostgreSQL 18.6
- **Graph DB**: Neo4j 2026.09.0-community
- **Policy Engine**: OPA 1.21.1 (Rego v1, `--watch`)
- **Container**: Podman with `podman compose` (`docker-compose.yml`)
- **CI**: GitHub Actions `.github/workflows/ci.yml` (Gradle build + tests, frontend lint + build, realm-as-code validation, `bash -n` on scripts)
- **Orchestration**: none (Kubernetes is ADR-012, not implemented)

## Development Setup

### Prerequisites
```bash
- Podman with `podman compose`
- Java 25 (or let the Gradle toolchain download it)
- Node.js 22+ (24 recommended) for the frontend
- Gradle: use the wrapper (9.8.1)
```

### Repository Structure
```
corporate-travel-portal/           # Monorepo root
├── build.gradle                    # Root Gradle config (Boot 4.1.1 + Cloud 2025.1.3 BOMs)
├── settings.gradle                 # Multi-project setup
├── docker-compose.yml              # All services (podman compose)
├── .env.example                    # Environment template (secrets, demo password, encryption key)
├── .github/workflows/ci.yml        # CI
│
├── infrastructure/
│   ├── databases/init.sql          # Creates the keycloak schema only; services use Flyway
│   ├── keycloak/
│   │   ├── config/corporate-travel.json        # Realm-as-code (keycloak-config-cli)
│   │   └── fgap/apply-delegation-permissions.sh # FGAP v2 permissions (kcadm.sh)
│   └── opa/policies/               # Rego policies
│
├── services/
│   ├── shared/
│   │   ├── security-commons/       # JWT converter, DelegatedActorFilter, OpaClient, SecurityConfig
│   │   └── domain-models/          # Shared enums
│   ├── api-gateway/                # Spring Cloud Gateway (8000)
│   ├── travel-service/             # 8081
│   ├── expense-service/            # 8082
│   ├── delegation-service/         # 8083 (Postgres + Neo4j)
│   ├── consent-service/            # 8084
│   └── employee-bff/               # 8085 (grant store in schema bff)
│
├── frontend/nextjs/                # Next.js employee portal (3000, run outside compose)
│
├── scripts/
│   ├── setup-local.sh, cleanup.sh, get-token.sh, seed-data.sh, test-opa-policy.sh
│   ├── end-to-end-test/run-delegation-flow.sh       # Regression gate
│   ├── keycloak-realm-test/validate-realm-config.sh # Apply twice, idempotency, claims, delegation
│   └── spikes/keycloak-delegation-spike.sh          # ADR-024 spike
│
├── docs/                           # UPGRADE-PLAN, AUTH-AND-SESSION, AUTHORIZATION-GUIDE, ...
└── architecture-decision-records/  # ADR-001 – ADR-024
```

There is no approval-service; approvals live in travel-service and expense-service.

## Key Dependencies

### Spring Boot Services (Boot 4 modular starters)
```gradle
dependencies {
    implementation project(':services:shared:security-commons')
    implementation project(':services:shared:domain-models')

    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-resource-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'
}
```

- HTTP clients use `RestClient` (no WebClient, no `.block()`); `InternalHttpClientConfig` pins internal clients to HTTP/1.1.
- Jackson 3 packages: `tools.jackson.databind`.
- Versions come from the Boot BOM; no pinned Flyway, Mockito or OpenTelemetry versions.

### Neo4j Integration (Delegation Service)
```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-neo4j'
```
`Neo4jConfig` declares both a JPA and a `Neo4jTransactionManager`; SDN 8's `Neo4jTemplate` needs the latter.

## Configuration Management

### Application Profiles

**application.yml** (resource server, per service):
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/corporate-travel
          audiences:
            - travel-service        # the service's own client ID
  flyway:
    schemas: travel
    locations: classpath:db/migration

opa:
  url: http://localhost:8181
```

**application-docker.yml** overrides hosts (`postgres`, `keycloak`, `opa`) for the compose network.

### Environment Variables (`.env.example`)
```bash
POSTGRES_USER=admin
POSTGRES_PASSWORD=admin123
POSTGRES_DB=corporate_travel
NEO4J_AUTH=neo4j/password123

KC_BOOTSTRAP_ADMIN_USERNAME=admin
KC_BOOTSTRAP_ADMIN_PASSWORD=admin123

# Substituted into the realm-as-code JSON by keycloak-config
KC_DEMO_USER_PASSWORD=password123
KC_CLIENT_SECRET_EMPLOYEE_BFF=bff-service-secret-change-in-production
KC_CLIENT_SECRET_DELEGATION_GRANT=delegation-grant-secret-change-in-production
# ... one KC_CLIENT_SECRET_* per confidential client

# AES-256-GCM key for delegators' offline tokens (demo key; generate with openssl rand -base64 32)
BFF_GRANT_ENCRYPTION_KEY=...

SPRING_PROFILES_ACTIVE=docker
```

## Technical Constraints

### Database
- **PostgreSQL**: one schema per service (`travel`, `expense`, `consent`, `delegation`, `bff`) plus `keycloak`
- **Neo4j**: single database for the delegation graph
- **Migrations**: Flyway per service (ADR-023)

### Authentication & Authorization
- **Token Format**: JWT (RS256, Keycloak)
- **Access token lifetime**: 5 minutes; SSO session idle 30 min, max 10 h
- **Audience**: every service accepts only tokens with its own client ID in `aud`; the gateway accepts any of the four services it routes to
- **Delegation**: RFC 8693 tokens with `sub` = delegator, `act.sub` = actor; actor verified via `X-Actor-Token` (ADR-024)
- **Sessions**: frontend uses an encrypted next-auth JWT cookie; the BFF keeps delegation context in a server-side session (JSESSIONID)
- **Authorization**: external (OPA), not Keycloak Authorization Services

### API Design
- **Style**: REST + JSON, unversioned paths (`/api/bookings`, `/api/expenses`, `/api/bff/...`)
- **Errors**: `GlobalExceptionHandler` per service; the BFF passes downstream 4xx through and maps 5xx to 502

## Development Workflow

### Local Development
```bash
# 1. Start the stack (realm and FGAP applied automatically)
./scripts/setup-local.sh          # or: podman compose up -d
./scripts/seed-data.sh

# 2. Run a service locally
./gradlew :services:travel-service:bootRun

# 3. Get a token (password grant on the confidential employee-bff client)
./scripts/get-token.sh alice.employee password123   # prints claims and an export TOKEN=... line
curl -H "Authorization: Bearer $TOKEN" http://localhost:8000/api/bookings

# 4. Frontend
cd frontend/nextjs && npm ci && npm run dev
```

### Building Services
```bash
./gradlew build                                   # build + tests
./gradlew :services:travel-service:build
podman compose up -d --build travel-service      # rebuild one image
```

### Testing Strategy
- **Unit Tests**: JUnit (Boot 4.1 BOM), Mockito, WireMock 3.13 for the Keycloak exchange; H2 in tests (no Testcontainers yet)
- **Realm**: `scripts/keycloak-realm-test/validate-realm-config.sh` (throwaway Keycloak; idempotency, claims, delegation, revocation; in CI)
- **OPA**: `scripts/test-opa-policy.sh`
- **E2E**: `scripts/end-to-end-test/run-delegation-flow.sh` (73 checks, live stack)

## Tool Usage Patterns

### Gradle Commands
```bash
./gradlew projects
./gradlew build -x test
./gradlew clean build
./gradlew :services:travel-service:test --tests BookingServiceTest
```

### Image Builds

Dockerfiles build from the repository root (they need `services/shared`). Stages:
1. **dependencies** — `gradle:9.8-jdk25-noble`, caches Gradle dependencies
2. **builder** — `gradle:9.8-jdk25-noble`, compiles the service
3. **runtime** — `eclipse-temurin:25-jre-noble`

```bash
podman build -t travel-service -f services/travel-service/Dockerfile .
```

### Compose Commands
```bash
podman compose up -d
podman compose up -d postgres keycloak
podman compose logs -f travel-service
podman compose restart travel-service
podman compose down
podman compose down -v          # deletes all local data
```

### Database Access
```bash
podman compose exec postgres psql -U admin -d corporate_travel
podman compose exec neo4j cypher-shell -u neo4j -p password123
```

### Keycloak Management
```bash
# Admin console (admin / admin123)
http://localhost:8080/admin

# Realm changes: edit infrastructure/keycloak/config/corporate-travel.json, then
podman compose up -d keycloak-config

# Health: /health/ready on management port 9000 (not published to the host; used by the compose healthcheck)

# Standard token exchange (audience scoping) with the employee-bff client
curl -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
  -d "subject_token=$TOKEN" \
  -d "subject_token_type=urn:ietf:params:oauth:token-type:access_token" \
  -d "audience=travel-service" \
  -d "client_id=employee-bff" \
  -d "client_secret=bff-service-secret-change-in-production"
```

A delegated exchange additionally sends `actor_token` / `actor_token_type` and needs a subject token carrying `may_act` (see ADR-024 and `scripts/lib/delegation-grant.sh`).

### OPA Testing
```bash
./scripts/test-opa-policy.sh
curl -X POST http://localhost:8181/v1/data/corporate/travel/authorization/allow \
  -H "Content-Type: application/json" -d @test-input.json
```

## Integration Points

### Service-to-Service Communication
- **Style**: synchronous REST over HTTP/1.1 (`RestClient`)
- **Auth**: user tokens or BFF token-exchange results. expense-service → travel-service budget lookup uses a client-credentials token (role `budget_reader`, `tenantId` query param; fails closed with 503), see ADR-009
- **Discovery**: compose DNS names
- **Resilience**: none (no circuit breaker or retry library)

### External Systems
- **Keycloak**: OIDC, token exchange, offline tokens, RFC 7009 revocation
- **OPA**: REST API for policy decisions
- **PostgreSQL**: JDBC; **Neo4j**: Bolt

## Security Considerations

### Secrets Management
- **Current**: `.env` and `application.yml` defaults (`*-secret-change-in-production`); committed demo `BFF_GRANT_ENCRYPTION_KEY`
- **Planned**: ADR-021 (Vault or OpenBao), not implemented

### Network Security
- Compose internal network; plain HTTP; no mTLS (ADR-009 not implemented)

## Observability
- **Logging**: plain Spring Boot console logs
- **Metrics**: Actuator `health,info,metrics`
- **Tracing**: none (ADR-020 not implemented; candidates: Keycloak OTel tracing, Spring Boot 4 OTel, `grafana/otel-lgtm`)

## Deployment
- **Current**: `podman compose up -d`
- **Kubernetes**: not implemented (ADR-012)
