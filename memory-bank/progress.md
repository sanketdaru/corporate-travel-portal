# Progress

**Last Updated**: 2026-04-04  
**Overall Status**: MVP Complete — 71/71 E2E tests passing

---

## What Works

### Infrastructure (Complete)

- PostgreSQL 15 running with all schemas (`travel`, `expense`, `consent`, `delegation`, `keycloak`)
- Neo4j 5.15 running for delegation graph
- Keycloak 23.0 with `corporate-travel` realm imported from authoritative `realm-export.json`
- OPA running with `authorization.rego`, hot-reload via `--watch`
- All services healthy in Docker Compose
- `realm_access.roles` delivered via `oidc-usermodel-realm-role-mapper` on `user-attributes` client scope

### Build System (Complete)

- Gradle multi-project build, Gradle wrapper 8.5
- Shared libraries: `security-commons`, `domain-models`
- All 6 application services build and pass unit tests

### Services (All Complete)

| Service | Port | Status | Notes |
|---------|------|--------|-------|
| api-gateway | 8000 | ✅ | Spring Cloud Gateway, JWT validation, routing |
| travel-service | 8081 | ✅ | Bookings CRUD, audit trail, delegation headers |
| expense-service | 8082 | ✅ | Expenses + items, workflow, audit trail |
| delegation-service | 8083 | ✅ | JPA + Neo4j dual-write, chain traversal |
| consent-service | 8084 | ✅ | CRUD, purpose binding, scheduler, OPA |
| employee-bff | 8085 | ✅ | Token Exchange V2, delegation context, API aggregation |

### Security Infrastructure (Complete)

- `SecurityContext` — JWT claims + delegation headers (`subjectId`, `delegationId`, `consentId`)
- `JwtAuthenticationConverter` — standard overload + header-aware overload for mutation endpoints
- `OpaClient` — authorization policy evaluation
- OPA policies: tenant isolation, RBAC, `is_resource_owner`, `is_active_delegate`, consent scope validation

### Audit Trail (Complete — ADR-011)

- `booking_audit` and `expense_audit` tables (Flyway V2 migrations)
- `BookingAuditService` / `ExpenseAuditService` wired into service impls
- Audit writes share the booking/expense transaction (FK constraint satisfied)
- `GET /api/bookings/{id}/audit` and `GET /api/expenses/{id}/audit` endpoints
- Verified: `actorId=dave.assistant`, `subjectId=carol.executive`, `delegationId`, `consentId` all correct for delegated booking

### Token Exchange (Complete — ADR-004)

- Standard Token Exchange V2 (`KC_FEATURES=token-exchange-standard`) via `KeycloakTokenExchangeClient`
- `employee-bff` Keycloak client: standard exchange enabled, `aud` mapper → `travel-service`
- `preferred_username` mapper on `user-attributes` scope
- `DelegationContextService` validates delegation + consent before exchange
- Session-scoped `DelegationContext` in BFF

### Keycloak Configuration (Complete — Phase 4)

- 5 human users seeded: alice, bob, carol, dave, eve — all with correct roles and `tenant_id` attribute
- `realm-export.json` is a clean authoritative export from live KC; validated by `validate-realm-export.sh` (65/65 checks)
- `realm_access.roles` available in all tokens (direct + exchanged)

### End-to-End Regression (Complete)

- `scripts/end-to-end-test/run-delegation-flow.sh` — 71/71 assertions
- 10 phases: direct CRUD, delegation setup, consent setup, token exchange, delegated booking, OPA owner/delegate rules, audit trail
- `scripts/kc-realm-export-test/validate-realm-export.sh` — 65/65 realm checks

### Documentation (Complete — Phase 3/4)

- `README.md` — system overview, architecture, quick start, demo scenarios
- `IMPLEMENTATION.md` — patterns, OPA integration, audit pattern, token exchange, troubleshooting
- `DELEGATION-FLOW.md` — step-by-step delegation flow guide (Carol + Dave)
- `GETTING-STARTED.md` — quick start and common commands
- Service READMEs: travel-service, expense-service, delegation-service, consent-service, api-gateway, employee-bff
- 23 ADRs covering all architectural decisions

---

## What's Left to Build (Post-MVP)

### Frontend (Not Started)

- Next.js 14 + React 18 employee portal
- NextAuth.js with Keycloak provider
- Booking, expense, delegation management UI

### Approval Service (Not Started)

- Multi-step approval workflow state machine
- Integration with travel-service and expense-service

### Advanced Features (Deferred)

- Keycloak SPI for token enrichment (ADR-022)
- OpenTelemetry distributed tracing (ADR-020)
- HashiCorp Vault (ADR-021)
- Kubernetes deployment / Helm charts (ADR-012)
- Workload identity / mTLS (ADR-009)
- Identity brokering / external IdP (ADR-008)

---

## Known Issues

None currently outstanding. All infrastructure and services are building and running correctly.

### Resolved Issues (Historical)

- **Keycloak `realm_access.roles` missing from tokens**: Required adding `oidc-usermodel-realm-role-mapper` to the `user-attributes` client scope. Without it, `has_role("employee")` in OPA fails for all users. Resolved Phase 4.
- **`update_booking` / `delete_booking` OPA rules missing**: Added `is_resource_owner` and `is_active_delegate` rules. Resolved Phase 4.
- **Delegation headers dropped on mutation endpoints**: `PUT /api/bookings/{id}/status` and `DELETE /api/bookings/{id}` now pass `HttpServletRequest` to the header-aware `extractSecurityContext` overload. Resolved Phase 4.
- **OPA hot-reload**: `--watch` flag added to OPA container in `docker-compose.yml`. Resolved Phase 4.
- **Standard Token Exchange V2**: `KC_FEATURES=token-exchange-standard` (not `token-exchange`). Resolved Phase 2.
- **Gradle Wrapper**: Upgraded from 4.4.1 to 8.5 for Java 17 compatibility. Resolved Phase 1.
- **Flyway `flyway-database-postgresql` version**: Added explicit version to root `build.gradle` dependency management. Resolved Phase 1.
