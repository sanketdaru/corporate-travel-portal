# Progress

**Last Updated**: 2026-10-08  
**Overall Status**: MVP complete and upgraded (phases 0–6 of `docs/UPGRADE-PLAN.md`); e2e 73/73, 262 unit tests

---

## What Works

### Infrastructure

- PostgreSQL 18.6 with schemas `travel`, `expense`, `consent`, `delegation`, `bff` (Flyway) and `keycloak` (`init.sql`)
- Neo4j 2026.09.0-community for the delegation graph
- Keycloak 26.8.0 with preview features `token-exchange-delegation,parameterized-scopes`
- Realm-as-code: `keycloak-config` one-shot applies `infrastructure/keycloak/config/corporate-travel.json` on every `podman compose up`; `keycloak-fgap` applies FGAP v2 delegation permissions; app services wait for both
- OPA 1.21.1 with `authorization.rego`, hot-reload via `--watch`
- All images pinned; stack runs with `podman compose`
- CI: `.github/workflows/ci.yml` (backend, frontend, realm-as-code, scripts)

### Build System

- Gradle 9.8.1 wrapper, Java 25 toolchain, Spring Boot 4.1.1, Spring Cloud 2025.1.3
- Shared libraries: `security-commons`, `domain-models`
- All 6 application services build and pass unit tests

### Services

| Service | Port | Status | Notes |
|---------|------|--------|-------|
| api-gateway | 8000 | ✅ | Spring Cloud Gateway 5, JWT + `aud` validation (any routed service), routing |
| travel-service | 8081 | ✅ | Travel authorizations, audit trail, RFC 8693 delegation |
| expense-service | 8082 | ✅ | Expenses + items, workflow, audit trail, budget check via travel-service |
| delegation-service | 8083 | ✅ | JPA + Neo4j dual-write, chain traversal |
| consent-service | 8084 | ✅ | CRUD, purpose binding, scheduler, OPA |
| employee-bff | 8085 | ✅ | Delegation grant store, RFC 8693 delegated exchange per audience, API aggregation |
| frontend (`frontend/nextjs`) | 3000 | ✅ | Next.js 16.4 / React 19.3, next-auth 5 beta.32; runs outside compose |

There is no approval-service; approvals live in travel-service and expense-service.

### Security Infrastructure

- `JwtAuthenticationConverter` — reads RFC 8693 `act` as an object; delegated tokens get no Spring authorities
- `DelegatedActorFilter` — verifies `X-Actor-Token` (`sub == act.sub`, same tenant, not itself delegated)
- `SecurityContext` — actor identity and roles from the verified actor token; `subjectId` from the delegated token's `sub`; `delegationId`, `consentId`, `purpose` from audit headers
- `OpaClient` (RestClient) — policy evaluation
- OPA policies: tenant isolation, RBAC, `is_resource_owner`, `is_active_delegate`, consent scope validation

### Audit Trail (ADR-011)

- `booking_audit` and `expense_audit` tables (Flyway V2)
- Audit writes share the booking/expense transaction
- `GET /api/bookings/{id}/audit` and `GET /api/expenses/{id}/audit`
- Verified: delegated booking records `userId`=Carol, `createdBy`=Dave via the `act` token

### Delegation (ADR-004, ADR-024)

- Grant ceremony: delegator signs in through the `delegation-grant` client (consent screen, `offline_access delegation:user:<delegate>`); BFF stores her offline token AES-256-GCM encrypted
- Activation (delegate only): BFF refreshes the offline token and exchanges it with the actor's token as `actor_token`, once per audience (`sub`=Carol, `act.sub`=Dave)
- Revocation: delegator only; revokes the Keycloak offline session; activation afterwards returns 409
- `X-Delegated-Subject` removed; spoofed headers are ignored

### Keycloak Configuration

- 5 demo users: alice, bob, carol, dave, eve, with roles and `tenant_id`, `employee_id`, `reports_to`, `assists` (declared in the user profile)
- Standard scopes (`basic`, `profile`, `email`, `roles`, `web-origins`, `acr`) assigned; tokens carry `email`, `name`, realm roles
- Audience mappers add the four service audiences to portal tokens
- Client secrets and demo password come from `.env`

### Regression and Validation

- `scripts/end-to-end-test/run-delegation-flow.sh` — 73/73 checks
- `scripts/keycloak-realm-test/validate-realm-config.sh` — 14/14 (idempotency, 65 claim checks, PKCE exchange, FGAP, grant, delegated exchange, revocation, cross-tenant denial)
- `scripts/test-opa-policy.sh` — 5/5
- `scripts/seed-data.sh` — clean

### Documentation

- `README.md`, `GETTING-STARTED.md`, `IMPLEMENTATION.md`
- `docs/`: `UPGRADE-PLAN.md`, `AUTH-AND-SESSION.md`, `AUTHORIZATION-GUIDE.md`, `TRAVEL-AUTHORIZATION.md`, `journeys/`
- Service READMEs
- ADR-001 – ADR-024

---

## What's Left to Build

### Upgrade Phase 7

- Docs and ADR status sync (in progress)

### Not Implemented ADRs (status "Proposed")

- Identity brokering / external IdP (ADR-008)
- Workload identity / mTLS (ADR-009)
- Kubernetes / Helm (ADR-012)
- OpenTelemetry (ADR-020)
- Vault / OpenBao (ADR-021)
- Keycloak SPI token enrichment (ADR-022) — may be unnecessary given `act` + standard mappers

### Follow-ups (`docs/UPGRADE-PLAN.md` §8)

- Testcontainers 2 + `testcontainers-keycloak` integration tests (tests use H2 today)
- Keycloak Organizations for tenancy (ADR-003)
- DPoP for the BFF; client secret rotation
- Re-evaluate Better Auth; Next.js Cache Components

---

## Known Issues

- **Keycloak delegation is a preview feature** (`token-exchange-delegation`); behaviour may change in later 26.x releases. Keycloak is pinned to 26.8.0.
- **Demo encryption key**: `BFF_GRANT_ENCRYPTION_KEY` has a committed default; client secrets are `*-secret-change-in-production`.
- **Offline sessions** expire after 30 days idle; the delegator must re-authorize.
- **Neo4j `DelegationRelationship`** uses deprecated Long internal ids (SDN warning); moving to `elementId`/UUID is a data-model change.
- **React Compiler** skips the component that uses react-hook-form's `watch()` (1 informational lint warning).
- **Frontend refactors** from Phase 6 have not had a full manual click-through.
- **WebAuthn `requireResidentKey` deprecation** warning on migrated realms (harmless; fresh realms are clean).
- **keycloak-config-cli** 6.5.1 is built against Keycloak 26.5.5; works with 26.8.0 for this realm. Fallback: Terraform provider.
- **Seed/e2e data** hardcodes 2026 dates (no server-side date validation, so tests still pass).
- **expense-service → travel-service** budget lookup sends no token (network trust).

### Resolved Issues (Historical)

- **Neo4j writes silently failing** after Boot 4 (no `Neo4jTransactionManager` next to JPA's). Fixed in Phase 5 (`Neo4jConfig`).
- **Jackson 3** creator detection (`@Builder.Default` lost) and trailing-token rejection (`seed-data.sh`). Fixed in Phase 3.
- **No audience validation**; activate endpoint returned tokens. Fixed in Phase 4.
- **Realm drift** (`--import-realm` skipped on existing realms; Admin API patches in `seed-data.sh`). Replaced by realm-as-code in Phase 2.
- **Undeclared user-profile attributes** (`employee_id` missing from tokens). Fixed in Phase 2.
- **`get-token.sh`** used the PKCE-only portal client. Fixed in Phase 0.
- **`realm_access.roles` missing**, missing OPA owner/delegate rules, OPA hot-reload — fixed before the upgrade.
