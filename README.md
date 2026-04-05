# Corporate Travel & Expense Platform — Identity Reference Implementation

A comprehensive multi-tenant Corporate Travel & Expense platform demonstrating advanced identity and access management patterns using Keycloak, OAuth 2.0 Token Exchange, Open Policy Agent, and microservices architecture.

**Status**: MVP Complete — 71/71 E2E tests passing (Phase 4, 2026-04-04)

## Overview

This project implements a reference architecture for enterprise identity management featuring:

- **Federated Identity** with Keycloak as central IAM
- **Delegated Identity** using OAuth 2.0 Token Exchange (RFC 8693) — Standard Token Exchange V2
- **Multi-Tenant Isolation** with single realm strategy
- **Fine-Grained Authorization** with Open Policy Agent (OPA)
- **Backend-for-Frontend (BFF)** pattern with delegation context injection
- **Microservices Architecture** with Spring Boot
- **Comprehensive Audit Trail** — `actorId`, `subjectId`, `delegationId`, `consentId` on every mutation

## Table of Contents

- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Services](#services)
- [Demo Scenarios](#demo-scenarios)
- [Development](#development)
- [Testing](#testing)
- [Architecture Decision Records](#architecture-decision-records)

## Architecture

### System Architecture

```
┌──────────────────────────────────────────────────┐
│  Employee BFF (Spring Boot, Port 8085)           │
│  Token Exchange · Delegation Context · API Agg.  │
└───────────────────┬──────────────────────────────┘
                    │ direct service calls (delegation-aware)
     ┌──────────────┼─────────────────────────────┐
     │              │                             │
┌────▼─────────────────────────────────────────┐  │
│         API Gateway (Port 8000)              │  │
│    (JWT Validation, Routing, Circuit Break)  │  │
└────┬─────────────────────────────────────────┘  │
     │                                            │
 ┌───▼───┐  ┌───────┐  ┌──────────┐  ┌──────────▼┐
 │Travel │  │Expense│  │Consent   │  │Delegation │
 │Service│  │Service│  │Service   │  │Service    │
 │:8081  │  │:8082  │  │:8084     │  │:8083      │
 └───┬───┘  └───┬───┘  └──────────┘  └─────┬─────┘
     │          │                           │
     └──────────┴───────────────────────────┘
                        │                    │
               ┌────────▼───────┐  ┌────────▼──────┐
               │   PostgreSQL   │  │     Neo4j     │
               │   Port 5432    │  │   Port 7687   │
               └────────────────┘  └───────────────┘

         ┌──────────────┐          ┌──────────────┐
         │   Keycloak   │          │     OPA      │
         │   Port 8080  │          │   Port 8181  │
         └──────────────┘          └──────────────┘
```

### Key Identity Patterns

1. **Authentication**: Keycloak handles user authentication; `realm_access.roles` delivered via `oidc-usermodel-realm-role-mapper` on `user-attributes` scope
2. **Token Exchange**: BFF performs Standard Token Exchange V2 (RFC 8693) — Dave's token becomes Carol's audience-scoped delegation token
3. **Authorization**: OPA evaluates policies including tenant isolation, role checks, ownership, and delegation scope
4. **Multi-Tenancy**: Single realm, `tenant_id` in all DB tables + JWT, enforced at OPA
5. **Consent Management**: External consent-service validates purpose-bound delegation before token exchange
6. **Audit Trail**: Every mutation writes `actorId`/`subjectId`/`delegationId`/`consentId` to audit tables

## Prerequisites

- **Docker** 24.0+ and Docker Compose
- **Java** 17+ (for local development)
- **Gradle** 8.5+ (wrapper included)
- **Git**

### System Resources

- Minimum 8 GB RAM (Neo4j + Keycloak + 5 Spring Boot services)
- 20 GB available disk space

## Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd corporate-travel-portal
```

### 2. Start All Services

```bash
# Start everything (infrastructure + application services)
docker-compose up -d

# Wait for all services to be healthy (60-90 seconds)
docker-compose ps
```

### 3. Verify Infrastructure

| Service | URL | Credentials |
|---------|-----|-------------|
| Keycloak Admin | http://localhost:8080/admin | admin / admin123 |
| Neo4j Browser | http://localhost:7474 | neo4j / password123 |
| OPA Health | http://localhost:8181/health | — |
| API Gateway | http://localhost:8000/actuator/health | — |
| Employee BFF | http://localhost:8085/actuator/health | — |

### 4. Build Services (for local development)

```bash
./gradlew build

# Build specific service
./gradlew :services:travel-service:build
```

### 5. Run End-to-End Delegation Regression Test

```bash
./scripts/end-to-end-test/run-delegation-flow.sh
# Expected: 71/71 assertions passing
```

## Project Structure

```
corporate-travel-portal/
├── architecture-decision-records/   # ADRs documenting architectural choices
├── infrastructure/
│   ├── databases/                   # PostgreSQL init scripts
│   ├── keycloak/                    # realm-export.json (authoritative)
│   └── opa/                         # OPA authorization policies (authorization.rego)
├── services/
│   ├── shared/
│   │   ├── security-commons/        # JWT handling, OPA client, SecurityContext
│   │   └── domain-models/           # Shared enums (BookingStatus, ExpenseStatus, …)
│   ├── api-gateway/                 # Spring Cloud Gateway (Port 8000)
│   ├── travel-service/              # Travel bookings + audit (Port 8081)
│   ├── expense-service/             # Expense management + audit (Port 8082)
│   ├── delegation-service/          # Delegation graph (PostgreSQL + Neo4j) (Port 8083)
│   ├── consent-service/             # Consent lifecycle + purpose binding (Port 8084)
│   └── employee-bff/                # BFF — token exchange + API aggregation (Port 8085)
├── scripts/
│   ├── end-to-end-test/             # run-delegation-flow.sh — 71 E2E assertions
│   ├── kc-realm-export-test/        # validate-realm-export.sh — 65 realm checks
│   ├── get-token.sh                 # JWT token retrieval for manual testing
│   ├── test-opa-policy.sh           # OPA policy validation
│   ├── setup-local.sh               # Infrastructure setup
│   └── cleanup.sh                   # Environment cleanup
├── memory-bank/                     # Project context (projectbrief, systemPatterns, …)
├── docker-compose.yml
├── build.gradle
└── settings.gradle
```

## Services

### Core Infrastructure

#### Keycloak (IAM Platform) — Port 8080
- Realm: `corporate-travel`
- 5 human users seeded: `alice.employee`, `bob.manager`, `carol.executive`, `dave.assistant`, `eve.employee`
- Default password: `password123`
- Standard Token Exchange V2 enabled on `employee-bff` client (`KC_FEATURES=token-exchange-standard`)
- `realm_access.roles` in all tokens via `oidc-usermodel-realm-role-mapper` on `user-attributes` scope

#### PostgreSQL — Port 5432
Schemas: `travel`, `expense`, `consent`, `delegation`, `keycloak`

#### Neo4j — Port 7474 / 7687
Graph database for delegation chains (`User` nodes, `CAN_ACT_AS` relationships)

#### OPA — Port 8181
Rego policies: multi-tenant isolation, RBAC, delegation-aware rules (`is_resource_owner`, `is_active_delegate`), consent scope validation. Hot-reload via `--watch`.

### Application Services

#### API Gateway — Port 8000
Spring Cloud Gateway; JWT validation, path-based routing to travel-service and expense-service, circuit breakers, security headers.

#### Travel Service — Port 8081
Bookings CRUD with OPA authorization. Delegation-aware via `X-Delegated-Subject` / `X-Delegation-Id` headers. Audit trail on CREATE, STATUS_CHANGE, DELETE. See [services/travel-service/README.md](services/travel-service/README.md).

#### Expense Service — Port 8082
Expense reports + line items, submit/approve/reject/pay workflow. Delegation-aware. Audit trail on all mutations. See [services/expense-service/README.md](services/expense-service/README.md).

#### Delegation Service — Port 8083
Delegation CRUD with PostgreSQL (source of truth) + Neo4j (graph traversal). Dual-write on create/revoke. Chain traversal via Cypher. See [services/delegation-service/README.md](services/delegation-service/README.md).

#### Consent Service — Port 8084
Consent lifecycle: grant, validate, revoke, expire. Purpose binding and scope validation. Auto-expiry scheduler. See [services/consent-service/README.md](services/consent-service/README.md).

#### Employee BFF — Port 8085
Standard Token Exchange V2 (RFC 8693), delegation context management (session-scoped), delegation-aware API aggregation for bookings and expenses. See [services/employee-bff/README.md](services/employee-bff/README.md).

## Demo Scenarios

### Scenario 1: Basic Employee Flow

```bash
# 1. Get Alice's token
TOKEN=$(./scripts/get-token.sh alice.employee password123 | grep "access_token" | ...)

# 2. Create a booking
curl -X POST http://localhost:8081/api/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bookingType":"FLIGHT","destination":"New York","startDate":"2026-06-01","endDate":"2026-06-05","totalAmount":500.00}'

# 3. Create an expense
curl -X POST http://localhost:8082/api/expenses \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"NYC Trip","description":"Client meeting"}'
```

### Scenario 2: Delegation Flow (Carol + Dave)

For a complete step-by-step walkthrough see [DELEGATION-FLOW.md](DELEGATION-FLOW.md).

**Summary**:
1. Carol grants a delegation to Dave (via delegation-service)
2. Carol grants consent with scope `book_travel` / `create_bookings` (via consent-service)
3. Dave authenticates and gets his own JWT
4. Dave calls `POST /api/bff/delegation/activate/{delegationId}?audience=travel-service` on BFF
5. BFF performs Standard Token Exchange V2 — exchanges Dave's token for a delegation token scoped to `travel-service`
6. Dave calls `POST /api/bff/bookings` — BFF injects delegation headers
7. Booking is created under Carol; audit record shows `actorId=dave.assistant`, `subjectId=carol.executive`

### Scenario 3: Multi-Tenant Isolation

1. Login as Alice (Tenant A) and create a booking
2. Login as Eve (Tenant B) and attempt to retrieve Alice's booking
3. OPA blocks the request: `tenant_id` mismatch

## Development

### Build Commands

```bash
./gradlew build                                         # Build all services
./gradlew :services:travel-service:build                # Build one service
./gradlew test                                          # Run all unit tests
./gradlew clean build                                   # Clean build
```

### Running Services Locally

```bash
# Start infrastructure only
docker-compose up -d postgres neo4j keycloak opa

# Run service with Spring Boot
./gradlew :services:travel-service:bootRun
```

### Database Migrations

All services use Flyway. Migrations live in `src/main/resources/db/migration/` per service. They apply automatically on startup.

## Testing

### Test Users

| Username | Password | Role | Tenant | Purpose |
|----------|----------|------|--------|---------|
| alice.employee | password123 | employee | tenant-a | Standard employee |
| bob.manager | password123 | manager | tenant-a | Manager with approval rights |
| carol.executive | password123 | executive | tenant-a | Delegation subject |
| dave.assistant | password123 | assistant | tenant-a | Delegation actor |
| eve.employee | password123 | employee | tenant-b | Tenant isolation testing |

### End-to-End Regression

```bash
./scripts/end-to-end-test/run-delegation-flow.sh
```

The script runs 10 phases covering:
- Direct booking and expense creation (alice, carol)
- Delegation setup (carol → dave)
- Consent setup
- Token exchange via BFF
- Delegated booking creation
- OPA authorization for owner and delegate
- Audit trail verification (actorId, subjectId, delegationId, consentId)

### Manual Token Testing

```bash
# Get access token
curl -s -X POST "http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token" \
  -d "client_id=employee-portal" \
  -d "username=alice.employee" \
  -d "password=password123" \
  -d "grant_type=password" | jq -r '.access_token'

# Call API with token
curl -H "Authorization: Bearer <token>" http://localhost:8000/api/bookings
```

### OPA Policy Testing

```bash
curl -X POST http://localhost:8181/v1/data/corporate/travel/authorization/allow \
  -H "Content-Type: application/json" \
  -d @infrastructure/opa/test-inputs/view_booking.json
```

## Architecture Decision Records

All architectural decisions are documented in [/architecture-decision-records/](./architecture-decision-records/):

| ADR | Title | Status |
|-----|-------|--------|
| ADR-001 | Corporate Travel & Expense as Reference Domain | Accepted |
| ADR-002 | Keycloak as Central IAM | Accepted |
| ADR-003 | Multi-Tenant Single Realm | Accepted |
| ADR-004 | OAuth 2.0 Token Exchange for Delegation | Accepted |
| ADR-005 | External Consent and Purpose Binding | Accepted |
| ADR-006 | Microservices with BFF Pattern | Accepted |
| ADR-007 / ADR-019 | OPA for External Authorization | Accepted |
| ADR-010 | Graph Database for Delegation Modeling | Accepted |
| ADR-011 | Comprehensive Audit and Compliance Ledger | Accepted |
| ADR-013 | Spring Boot as Backend Framework | Accepted |
| ADR-015 | PostgreSQL as Primary Database | Accepted |
| ADR-016 | Neo4j for Delegation Graph | Accepted |
| ADR-017 | API Gateway Pattern | Accepted |
| ADR-018 | BFF Strategy | Accepted |
| ADR-023 | Flyway for Database Migrations | Accepted |

## Security Considerations

### Production Checklist

- [ ] Change all default passwords
- [ ] Use HTTPS/TLS for all services
- [ ] Configure proper CORS policies
- [ ] Enable Keycloak brute-force protection
- [ ] Implement rate limiting
- [ ] Use HashiCorp Vault for secrets (ADR-021, deferred)
- [ ] Enable OPA audit logging
- [ ] Use workload identity for service-to-service auth (ADR-009, deferred)

### Critical Operational Notes

- `realm_access.roles` is **not** included in Keycloak tokens by default. It requires the `oidc-usermodel-realm-role-mapper` on the `user-attributes` client scope. If this mapper is removed, all OPA `has_role("employee")` checks will fail with 403.
- OPA **does not** hot-reload without the `--watch` flag. The flag is set in `docker-compose.yml`; a running OPA container without it requires a policy push via `PUT /v1/policies/...` or container restart.
- The `realm-export.json` is the authoritative Keycloak configuration. Use `validate-realm-export.sh` to verify a clean import (65/65 checks).

## Post-MVP Backlog

The following are out of scope for MVP and intentionally deferred:

- Frontend (Next.js employee portal) — ADR-014
- HashiCorp Vault — ADR-021
- OpenTelemetry distributed tracing — ADR-020
- Kubernetes deployment — ADR-012
- Keycloak SPI for token enrichment — ADR-022
- Workload identity (mTLS) — ADR-009
- Identity brokering (external IdP) — ADR-008

## Support

- GitHub Issues: [Project Issues]
- ADRs: [/architecture-decision-records/](./architecture-decision-records/)
- Delegation Flow: [DELEGATION-FLOW.md](DELEGATION-FLOW.md)
- Implementation Guide: [IMPLEMENTATION.md](IMPLEMENTATION.md)
