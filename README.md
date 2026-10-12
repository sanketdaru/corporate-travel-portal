# Corporate Travel & Expense Platform — Identity Reference Implementation

A multi-tenant travel and expense demo that shows enterprise identity patterns end to end: Keycloak as IAM, RFC 8693 delegation (one user acting for another), purpose-bound consent, OPA authorization and a full audit trail.

**Stack:** Java 25 · Spring Boot 4.1 · Spring Cloud 2025.1 · Keycloak 26.8 · PostgreSQL 18 · Neo4j 2026.09 · OPA 1.21 · Next.js 16.4 / React 19.3

## What it demonstrates

| Pattern | How |
|---|---|
| Central IAM | Keycloak, one realm (`corporate-travel`), tenants as groups + `tenant_id` claim |
| Realm as code | `infrastructure/keycloak/config/corporate-travel.json`, applied idempotently by keycloak-config-cli on every start |
| Delegation | Keycloak-native RFC 8693: delegated tokens carry `sub` = delegator, `act.sub` = actor ([ADR-024](architecture-decision-records/ADR-024:%20Adopt%20Keycloak%20Native%20RFC%208693%20Delegation.md)) |
| Consent | consent-service binds each delegation to a purpose and scopes |
| Token scoping | Every service accepts only tokens whose `aud` names it |
| Authorization | OPA (Rego v1): tenant isolation, roles, ownership, delegation and consent rules |
| Audit | Every mutation records `actorId`, `subjectId`, `delegationId`, `consentId` |

## Architecture

```
 Browser ── Next.js (3000) ──┬── /api/bff/* ──► Employee BFF (8085) ── delegation, token exchange, aggregation
                             └── /api/*     ──► API Gateway  (8000)
                                                     │
          ┌──────────────┬───────────────┬───────────┴───────────┐
     Travel (8081)  Expense (8082)  Delegation (8083)       Consent (8084)
          └──────────────┴──────┬────────┘   └─ Neo4j (7687)       │
                          PostgreSQL (5432) ◄───────────────────────┘
     Keycloak (8080)   OPA (8181)
```

## Quick start

Requires Podman (or Docker) with compose, ~8 GB RAM for the container VM, JDK 25 optional (Gradle downloads it), Node 22+ for the frontend.

```bash
cp .env.example .env                     # optional: override secrets and the grant encryption key
podman compose up -d                     # builds and starts everything (first run: several minutes)
podman compose ps                        # wait until all services are healthy
./scripts/seed-data.sh                   # demo bookings, expenses and the Carol → Dave delegation
./scripts/end-to-end-test/run-delegation-flow.sh   # regression suite (73 checks)
```

Frontend (runs outside compose):

```bash
cd frontend/nextjs && cp .env.example .env.local && npm ci && npm run dev    # http://localhost:3000
```

| Endpoint | URL | Credentials |
|---|---|---|
| Frontend | http://localhost:3000 | any test user below |
| Keycloak admin | http://localhost:8080/admin | admin / admin123 |
| Neo4j browser | http://localhost:7474 | neo4j / password123 |
| Gateway / BFF health | http://localhost:8000/actuator/health, http://localhost:8085/actuator/health | — |

Startup order is handled by compose: Keycloak → `keycloak-config` (realm) → `keycloak-fgap` (delegation permissions) → services.

## Test users

Password for all: `password123`.

| User | Roles | Tenant | Role in demo |
|---|---|---|---|
| alice.employee | employee | tenant-a | Employee |
| bob.manager | employee, manager | tenant-a | Approves expenses |
| carol.executive | employee, executive | tenant-a | Delegator |
| dave.assistant | employee, assistant | tenant-a | Delegate (actor) |
| eve.employee | employee | tenant-b | Tenant isolation |

## Delegation in one minute

1. **Carol** grants Dave a delegation (purpose, scopes, expiry) and the matching consent.
2. **Carol authorizes it in Keycloak:** a consent screen ("Delegate token to … dave"). The BFF stores her offline token, encrypted.
3. **Dave** activates the delegation. The BFF exchanges Carol's token with Dave's token as `actor_token`, once per service.
4. Dave's requests now carry tokens with `sub` = Carol, `act.sub` = Dave. Services verify Dave's own token (`X-Actor-Token`) and authorize *Dave* acting for *Carol* — with Dave's roles, never Carol's.
5. **Carol revokes** through the BFF: the delegation, her Keycloak offline session and the stored token are removed.

Keycloak only allows delegation within a tenant. Walkthrough: [DELEGATE-JOURNEY.md](docs/journeys/DELEGATE-JOURNEY.md).

## Project structure

```
architecture-decision-records/   ADR-001 … ADR-024
docs/                            upgrade plan, auth/authorization guides, persona journeys
frontend/nextjs/                 Next.js app (next-auth, PKCE)
infrastructure/
  keycloak/config/               realm-as-code (keycloak-config-cli)
  keycloak/fgap/                 FGAP v2 delegation permissions (kcadm.sh)
  opa/policies/                  authorization.rego
  databases/                     Postgres init (keycloak schema)
services/
  shared/security-commons/       JWT → SecurityContext, delegated-actor verification, OPA client
  shared/domain-models/          shared enums
  api-gateway/                   Spring Cloud Gateway (8000)
  travel-service/                bookings + audit (8081)
  expense-service/               expenses + audit (8082)
  delegation-service/            delegations: PostgreSQL + Neo4j graph (8083)
  consent-service/               consent lifecycle (8084)
  employee-bff/                  delegation grants, token exchange, aggregation (8085)
scripts/
  seed-data.sh                   demo data (includes the Keycloak grant)
  end-to-end-test/               run-delegation-flow.sh
  keycloak-realm-test/           validate-realm-config.sh (realm apply, idempotency, claims, delegation)
  spikes/                        keycloak-delegation-spike.sh (ADR-024 evaluation)
  lib/delegation-grant.sh        scripted Keycloak grant, used by seed and e2e
  get-token.sh, test-opa-policy.sh, setup-local.sh, cleanup.sh
```

## Development

```bash
./gradlew build                               # all services, unit tests (JDK 25 toolchain)
./gradlew :services:travel-service:bootRun    # one service against the compose infrastructure
podman compose up -d postgres neo4j keycloak keycloak-config keycloak-fgap opa   # infrastructure only
./scripts/get-token.sh alice.employee         # a token for manual API calls
./scripts/keycloak-realm-test/validate-realm-config.sh   # realm-as-code checks on a throwaway Keycloak
./scripts/test-opa-policy.sh                  # OPA policy checks
```

- **Realm changes:** edit `infrastructure/keycloak/config/corporate-travel.json`; `podman compose up -d keycloak-config` applies them. No export/import.
- **Database changes:** Flyway migrations in each service's `src/main/resources/db/migration/`, applied on startup (the BFF owns schema `bff`).
- **CI** (`.github/workflows/ci.yml`): backend build + tests, frontend lint + build, realm validation, script syntax.

## Security notes

- All secrets and passwords in this repo are demo values. Override them via `.env`; in particular set your own `BFF_GRANT_ENCRYPTION_KEY` (`openssl rand -base64 32`) — it protects stored delegator offline tokens.
- Keycloak RFC 8693 delegation is a **preview** feature in 26.8; re-run `scripts/spikes/keycloak-delegation-spike.sh` when upgrading Keycloak.
- TLS, rate limiting, Vault/OpenBao (ADR-021), workload identity (ADR-009) and Kubernetes (ADR-012) are not implemented.

## Documentation

| Read | For |
|---|---|
| [GETTING-STARTED.md](GETTING-STARTED.md) | Setup in detail, troubleshooting |
| [AUTH-AND-SESSION.md](docs/AUTH-AND-SESSION.md) | Login (PKCE), token claims, sessions |
| [AUTHORIZATION-GUIDE.md](docs/AUTHORIZATION-GUIDE.md) | SecurityContext, OPA, delegation-aware rules |
| [TRAVEL-AUTHORIZATION.md](docs/TRAVEL-AUTHORIZATION.md) | Booking states, budgets, audit |
| [Persona journeys](docs/journeys/) | Employee, manager, delegate — UI to database |
| [UPGRADE-PLAN.md](docs/UPGRADE-PLAN.md) | 2026-10 platform upgrade: decisions and results per phase |
| [architecture-decision-records/](architecture-decision-records/) | Why things are the way they are |

Suggested reading order: AUTH-AND-SESSION → AUTHORIZATION-GUIDE → a persona journey.
