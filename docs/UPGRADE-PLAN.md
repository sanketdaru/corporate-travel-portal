# Upgrade Plan — Bring the Demo in Sync with Current Realities

Analysis date: 2026-10-08. All versions below were verified against the upstream release pages and registries on that date. Items marked **[verify]** need a check during the spike.

---

## 1. Executive Summary

The project works end to end, but it was built around Keycloak limitations that no longer exist, and its stack is roughly one major version behind.

The four biggest opportunities:

1. **Real RFC 8693 delegation in Keycloak.** Keycloak 26.7 added `actor_token` / `act` / `may_act` delegation, and 26.8 (released 2026-10-01) promoted it to **preview** (`token-exchange-delegation`). ADR-004 only exists because Standard V2 was audience-scoping only. The header-based workaround (`X-Delegated-Subject`, `X-Delegation-Id`) can now be replaced, or at least backed, by a token that carries an `act` claim.
2. **Realm-as-code.** Today the realm is created by a one-shot `--import-realm`. That import is skipped when the realm already exists, so `seed-data.sh` patches clients through the Admin API afterwards. A declarative, idempotent tool removes the drift.
3. **Platform majors.** Spring Boot 3.5.11 → 4.1.x, Spring Cloud 2025.0 → 2025.1 "Oakwood", Java 17 → 25 LTS. Spring Security 7 ships a first-class `TokenExchangeOAuth2AuthorizedClientProvider`, which replaces the hand-rolled BFF exchange client.
4. **Reproducibility.** Every infrastructure image uses a floating tag (`:latest`, `:community`). There is no CI. Several scripts and docs are stale or broken.

---

## 2. Current State (as found)

### 2.1 Backend

| Item | Current | Notes |
|---|---|---|
| Gradle wrapper | 9.3.1 | |
| Java toolchain | 17 (all modules, all Dockerfiles) | `gradle:9.3-jdk17`, `eclipse-temurin:17-jre-jammy` |
| Spring Boot | 3.5.11 | root `build.gradle:3,29` |
| Spring Cloud | 2025.0.1 | root `build.gradle:30` |
| Gateway | `spring-cloud-starter-gateway-server-webflux` | already on the new artifact name |
| springdoc | 2.8.15 | Boot 3 line |
| Flyway | 10.17.0 (pinned, overrides BOM) | root `build.gradle:39-40` |
| jjwt | 0.12.3 | `security-commons`; likely unused next to Nimbus **[verify]** |
| Mockito | 5.8.0 (pinned per service) | should come from BOM |
| WireMock | `wiremock-standalone` 3.0.1 | |
| Jacoco | 0.8.14, expense-service 0.8.11 | inconsistent; verification not wired into `check` |
| OpenTelemetry | `opentelemetry-api` 1.32.0 in BOM, never used | |
| Testcontainers | none | tests use H2 |
| CI | none | `.github/` holds only an untracked IDE hook |

Security findings in code:

- **No audience validation** in any resource server. The audience-scoped exchanged token gives no protection if services accept any `aud`.
- `JwtAuthenticationConverter.java:54` reads `act` as a **string** plus a non-standard `act_sub` claim. RFC 8693 defines `act` as a JSON object with `sub`.
- `KeycloakTokenExchangeClient` hand-rolls the exchange on WebClient and calls `.block()` inside a servlet app. Its Javadoc (L25-26) is stale.
- `security-commons` pulls in `starter-web` and `starter-webflux` as `api` dependencies, so every service gets both stacks.

### 2.2 Keycloak

| Item | Current |
|---|---|
| Image | `quay.io/keycloak/keycloak:latest` (realm export says 26.5.6) |
| Mode | `start-dev --import-realm` |
| Admin bootstrap | `KEYCLOAK_ADMIN*` (deprecated since 26.0) |
| Hostname | `KC_HOSTNAME_STRICT_HTTPS` (hostname v1, removed) |
| Features | `token-exchange-standard` (default since 26.2, redundant) |
| Health | probed on the realm URL; management port 9000 not used |
| Token exchange | V2, audience only, on `employee-bff` |
| FGAP | `adminPermissionsEnabled: false` |
| Organizations | off; tenancy via `/TenantA`, `/TenantB` groups + `tenant_id` attribute |
| Custom SPI (ADR-022) | not implemented |

Realm export findings:

- `standard.token.exchange.enabled: "true"` is set on **public** client `employee-portal` and on `travel-service` / `expense-service`. V2 rejects public requesters, and only `employee-bff` requests exchanges. The flags are noise, and `seed-data.sh:316-371` re-applies the one on `employee-portal`.
- **User-profile gap:** only `tenant_id` is declared. `employee_id`, `reports_to` and `assists` are undeclared, and the unmanaged-attribute policy is unset, so Keycloak may drop or hide them.
- Standard client scopes (`profile`, `email`, `roles`, `web-origins`, `acr`, `basic`) are absent and `defaultDefaultClientScopes` is empty. The realm-role mapper in `user-attributes` is a workaround for that, and it has a stray `user.attribute: "foo"`.
- The export contains hashed user passwords, built-in clients and service-account users. It is a database dump, not a curated definition.
- Client secrets are plaintext `*-secret-change-in-production`.

### 2.3 Infrastructure

| Service | Current | Issue |
|---|---|---|
| postgres | `postgres:latest` | floating; volume mount already in PG 18 style |
| neo4j | `neo4j:community` | floating; `NEO4J_dbms_memory_*` env names are 4.x style |
| opa | `openpolicyagent/opa:latest`, `platform: linux/amd64` | floating; emulated on Apple Silicon; healthcheck only runs `opa version` |
| Rego | `future.keywords` imports, `default allow = false` | parses on OPA 1.x but not idiomatic v1 |

### 2.4 Frontend

| Package | Current | Latest |
|---|---|---|
| next | 16.2.2 | 16.4.0 |
| react / react-dom | 19.2.4 | 19.3.0 |
| next-auth | 5.0.0-beta.30 | 5.0.0-beta.32 (still beta) |
| @auth/core | 0.41.0 | |
| tailwindcss | 4.2.2 | 4.3.3 |
| eslint | 9.39.4 | 10.12.0 |
| typescript | 5.9.3 | 7.0.2 (6.0.3 latest 6.x) |
| @types/node | ^20 | Node 24 is Active LTS; Node 26 becomes LTS 2026-10-28 |

Auth.js joined Better Auth in September 2025. Auth.js gets security fixes only, and v5 never left beta.

### 2.5 Broken or stale items

- `scripts/get-token.sh` uses a password grant on `employee-portal`, which has direct access grants disabled. It should fail with `unauthorized_client`, yet `GETTING-STARTED.md:68` tells users to run it.
- `scripts/setup-local.sh` calls `docker-compose` (the project uses `podman compose`). Its Keycloak wait loop curls `/health/ready` on port 8080; that 404s, but `curl -s` exits 0, so the check always passes.
- `validate-realm-export.sh:27` documents the wrong path.
- Docs that contradict the code: `IMPLEMENTATION.md:254` (`requested_subject`), `README.md:70` (claims `sub` becomes Carol), `memory-bank/techContext.md:20,345-351` (Keycloak 23, wrong secret), `progress.md:14`.
- ADRs 008, 009, 012, 020, 021 and 022 are "Accepted" but not implemented.

---

## 3. What Changed Upstream (relevant subset)

### 3.1 Keycloak 26.6 – 26.8

| Capability | Status | Relevance |
|---|---|---|
| Standard Token Exchange V2 | supported since 26.2 | in use |
| **Token exchange delegation** (`actor_token`, `act`, `may_act`) | experimental 26.7, **preview 26.8** (`token-exchange-delegation`) | replaces the header workaround in ADR-004 |
| FGAP v2 (`admin-fine-grained-authz:v2`) | supported since 26.2 | delegation authorization uses FGAP v2 scopes `delegate` / `delegate-members` |
| Legacy `token-exchange:v1` | preview, deprecated | keep avoiding |
| JWT Authorization Grant (RFC 7523) | supported 26.6 | future identity chaining across domains |
| Federated client auth + Kubernetes ServiceAccount tokens | supported 26.6 | ADR-009 workload identity, once on Kubernetes |
| SPIFFE JWT-SVID client auth | preview | ADR-009 |
| Admin API v2 (clients only) | preview 26.8 (`client-admin-api:v2`) | declarative, but clients only — too narrow today |
| Organizations | supported; org groups 26.6, shared IdPs 26.8 | ADR-003 multi-tenancy |
| OpenTelemetry tracing | supported since 26.1 | ADR-020 |
| DPoP | supported 26.4 | optional hardening |
| Client secret rotation | supported 26.8 | optional |
| Management port 9000 health | default; main-port option since 26.4 | fix healthchecks |
| Java | 21 baseline, 25 supported since 26.6 | |

Key facts about delegation in 26.8:

- The issued token keeps the subject as `sub` and records the actor in `act`.
- The subject token must carry `may_act` naming the actor.
- Two modes exist: admin delegation and client delegation. Client delegation uses the parameterized scope `delegation:client:<client-id>` with user consent.
- Standard exchange now **rejects** subject tokens that already carry delegation claims.

### 3.2 Realm-as-code options

| Option | Latest | Idempotent | Fit |
|---|---|---|---|
| `--import-realm` (current) | built-in | no — skipped if realm exists | current pain |
| `kc.sh import --override true` | built-in | overwrite, needs Keycloak stopped | poor for compose |
| Operator `KeycloakRealmImport` | built-in | create-only | poor |
| Admin API v2 / `KeycloakOIDCClient` CR | preview 26.8 | yes | clients only |
| **adorsys keycloak-config-cli** | 6.5.1 (2026-05-22), built against Keycloak 26.5.5 | yes, diff-based | consumes realm-representation JSON/YAML we already have; runs as a compose one-shot |
| **terraform-provider-keycloak** (official `keycloak` org) | 5.10.0 (2026-10-05) | yes, state-based | current with Keycloak; adds Terraform/OpenTofu tooling and state |

### 3.3 Platform

| Component | Target | Notes |
|---|---|---|
| Java | 25 LTS | Boot 4.1 supports 17–26; Gradle supports 25 from 9.1 |
| Gradle | 9.8.1 | |
| Spring Boot | 4.1.1 | Framework 7, Security 7.1, Jackson 3, Hibernate 7, JUnit 6, modular starters |
| Spring Cloud | 2025.1.3 "Oakwood" | Boot 4.1 needs ≥ 2025.1.2; Gateway 5.0.x |
| springdoc | 3.1.1 | 3.x is the Boot 4 line |
| Flyway | BOM-managed 12.4.0 | add `spring-boot-starter-flyway`; keep `flyway-database-postgresql` |
| Spring Data Neo4j | 8.1.x via BOM | Neo4j Java driver 6.1 |
| Testcontainers | 2.0.5 | modules renamed `testcontainers-*`; Keycloak module `com.github.dasniko:testcontainers-keycloak` |
| Postgres | 18 | 19 is in beta |
| Neo4j | 2026.09 | calendar versions |
| OPA | 1.21.1 | Rego v1 default |
| Node | 24 LTS now, 26 LTS from 2026-10-28 | |

---

## 4. Decisions

**Decided 2026-10-08:**

| # | Decision | Outcome |
|---|---|---|
| D1 | Realm-as-code tool | **keycloak-config-cli** |
| D2 | Keycloak version | **26.8.0 or later** (minimum 26.8.0; pin an exact patch in compose) |
| D3 | Delegation model | Spike first (Phase 5), as planned |
| D4 | Frontend auth library | **Stay on next-auth 5** (beta.32) |
| D5 | Spring Boot | **Upgrade to Boot 4.1** |
| D6 | TypeScript | **Stay on 6.0.x** |
| D7 | Tenancy | **Keycloak Organizations deferred**; keep groups + `tenant_id` |

Consequence of D1 + D2: because Keycloak 26.6 and older is ruled out, the Terraform fallback is the only option if keycloak-config-cli fails the Phase 2 smoke gate on 26.8. Pinning an older Keycloak is no longer an escape route.

Original analysis, kept for reference:

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D1 | Realm-as-code tool | **keycloak-config-cli**, with a smoke gate | The tool reads our existing realm JSON directly and runs as a compose init container with env-var substitution for secrets. Risk: its latest build targets Keycloak 26.5.5. Fallback is the Terraform provider, which tracks Keycloak releases closely. |
| D2 | Keycloak version | **Pin 26.8.0** | Delegation preview is 26.8-only. If D1's smoke gate fails on 26.8, either pin 26.6.x (no delegation) or switch D1 to Terraform. |
| D3 | Delegation model | **Spike first** (Phase 5), then decide | Open question: Keycloak delegation expects a subject token that carries `may_act`. In our flow the BFF has the actor's (Dave's) token, not the delegator's (Carol's). Admin delegation may cover this; client delegation needs Carol's consent. The spike must prove the Dave-for-Carol flow before ADR-004 changes. |
| D4 | Auth library for the frontend | **Stay on next-auth 5 beta.32** for now | Our stateless JWT session suits Auth.js. Better Auth needs a database. Revisit when a Better Auth migration has a concrete benefit. |
| D5 | Spring Boot 4 vs staying on 3.5.x | **Boot 4.1** | The 3.5 line is near the end of OSS support **[verify date]**. Boot 4 brings Security 7 token exchange support. |
| D6 | TypeScript major | **6.0.x**, not 7.0 | TS 7 is the native-compiler rewrite. Wait until Next.js and the ESLint plugins confirm support **[verify]**. |
| D7 | Tenancy model | Keep groups + `tenant_id` in this upgrade; evaluate Organizations as a follow-up | Organizations changes token claims and the OPA tenant checks. That is a feature change, not an upgrade. |

---

## 5. Phased Plan

Each phase ends green on the regression gate from Phase 0 before the next phase starts.

### Phase 0 — Baseline and guardrails

1. Add a GitHub Actions workflow: Gradle build + tests (Java 17 for now), frontend `npm ci && npm run lint && npm run build`.
2. Make `scripts/end-to-end-test/run-delegation-flow.sh` the regression gate. Run it against the current stack and record the passing baseline.
3. Fix `scripts/get-token.sh`: use `employee-bff` with its secret (same as the other scripts), or document the PKCE path.
4. Fix `setup-local.sh`: use `podman compose`, use `curl -f`, and probe the correct health endpoint.
5. Pin current images to what actually runs today (Keycloak `26.5.6` per the export, current Postgres and Neo4j majors) so later diffs are deliberate.
   Done: `postgres:18.6`, `neo4j:2026.09.0-community`, `quay.io/keycloak/keycloak:26.5.6`, `openpolicyagent/opa:1.21.1`. No local images were cached, so Postgres, Neo4j and OPA are pinned to the versions `:latest` resolved to on 2026-10-08. Keycloak is pinned to the export version, not `:latest` (which is now 26.8.0).

Exit: CI green; e2e script passes on pinned images.

**Phase 0 result (2026-10-08):**

- CI green on GitHub (backend, frontend, scripts). Local: 225 unit tests pass across 5 services.
- e2e baseline: `run-delegation-flow.sh` passes 71/71 checks on the pinned stack, after `seed-data.sh`.
- Pre-existing breakage fixed on the way: stale unit tests in travel-service, employee-bff and delegation-service; the e2e booking payload still used `totalAmount` / `bookingType` instead of the required `budget` field introduced by the travel-authorization refactor.
- Observations to carry forward:
  - Flyway 10.17 warns that PostgreSQL 18.6 is untested ("latest supported version of PostgreSQL is 16"). Resolved by the BOM-managed Flyway in Phase 3.
  - Neo4j 2026.09 logs deprecation warnings for `dbms.memory.*` settings. Rename in Phase 1.
  - Keycloak logs `KEYCLOAK_ADMIN` deprecation warnings. Fix in Phase 1.
  - employee-bff turns a downstream 400 into a 500 (`GlobalExceptionHandler` "Unexpected error"). It should pass through 4xx from `WebClientResponseException`. Fix in Phase 4.
  - Seed and e2e scripts hardcode 2026 dates, now mostly in the past. No server-side date validation exists, so they still pass.

### Phase 1 — Infrastructure modernization

1. Bump Keycloak to `quay.io/keycloak/keycloak:26.8.0`; switch OPA to `1.21.1-static` (Postgres and Neo4j were pinned in Phase 0).
2. Remove `platform: linux/amd64` from OPA.
3. Keycloak compose settings:
   - `KEYCLOAK_ADMIN*` → `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` (also in `.env.example`).
   - Remove `KC_HOSTNAME_STRICT_HTTPS`, `KC_HTTP_PORT`, and the redundant `token-exchange-standard` feature.
   - Healthcheck on management port 9000 `/health/ready`.
4. Neo4j env names → `NEO4J_server_memory_pagecache_size`, `NEO4J_server_memory_heap_max__size`.
5. **Postgres 18 data:** the existing `postgres_data` volume cannot be reused across a major version. For a demo, recreate the volume with `podman compose down -v`. Document this in the upgrade notes — it deletes all local data.
6. OPA: real healthcheck (`/health`); convert Rego to idiomatic v1 (`import rego.v1` or none, `default allow := false`); run `opa check --v1-compatible` and `scripts/test-opa-policy.sh`.

Exit: e2e gate passes on Keycloak 26.8.0 with the existing `--import-realm`.

**Phase 1 result (2026-10-08):**

- Keycloak 26.5.6 → 26.8.0. The existing database migrated in place (realm model 26.5 → 26.6.2 → 26.7.0 → 26.8.0).
- Compose: `KC_BOOTSTRAP_ADMIN_*`; removed `KC_HOSTNAME_STRICT_HTTPS`, `KC_HTTP_PORT`, `KC_FEATURES`; Keycloak healthcheck on management port 9000 `/health/ready`; Neo4j `server.memory.*` names; OPA runs native arm64 (`platform:` removed).
- Rego: `default allow := false`, `future.keywords` imports removed. `opa check --strict` passes on 1.21.1. The two `audit_entry = {...} if` rules keep `=` because `:=` rules cannot have multiple definitions. `opa fmt` would only change indentation; not applied.
- OPA kept on the non-static `1.21.1` image: both variants are distroless, and the `-static` variant brings no benefit here. The OPA healthcheck stays `opa version` because the image has no shell or HTTP client.
- Verified: OPA policy tests 5/5; e2e 71/71 on the migrated stack; fresh import into a clean Keycloak 26.8.0 + `validate-realm-export.sh` 65/65.
- New warning for Phase 2: Keycloak logs "WebAuthn policy option 'requireResidentKey' is deprecated … use 'residentKey'". It comes from the realm export.
- Postgres data volume: no action needed. The stack was already on Postgres 18 (pinned in Phase 0).
- Doc references to `KC_FEATURES=token-exchange-standard` and `platform: linux/amd64` remain in `README.md`, `IMPLEMENTATION.md`, `ADR-IMPLEMENTATION-PLAN.md`, ADR-004 and `memory-bank/`. They are handled in Phase 7.

### Phase 2 — Realm-as-code

1. Curate a realm definition from `realm-export.json`:
   - Remove built-in clients, service-account users, hashed passwords, generated IDs and default role internals.
   - Keep only `corporate-travel` specifics: clients, scopes, mappers, roles, groups, demo users with plain-text initial passwords (demo only), user profile.
2. Fix realm defects while curating:
   - Declare `employee_id`, `reports_to`, `assists` in the user profile.
   - Restore the standard client scopes (`profile`, `email`, `roles`, `basic`, `web-origins`, `acr`); drop the workaround role mapper or clean its config.
   - Remove `standard.token.exchange.enabled` from `employee-portal`, `travel-service`, `expense-service`. Keep it on `employee-bff` only.
3. Replace secrets with environment placeholders resolved from `.env`.
4. Add a `keycloak-config` one-shot compose service (D1) that runs after Keycloak is healthy. Application services depend on its successful completion.
5. Change Keycloak to plain `start-dev` (no `--import-realm`).
6. Delete the Admin API patching from `seed-data.sh:316-371`.
7. Turn `validate-realm-export.sh` into `validate-realm.sh`: run config-cli twice and assert the second run makes no changes (idempotency), then run the existing claim and exchange checks.
8. Optional: split the realm into files (clients, users, scopes) for readable diffs.

Exit: `podman compose up` on an existing database applies realm changes; second run is a no-op; e2e gate passes.

**Phase 2 result (2026-10-08):**

- New `keycloak-config` one-shot compose service (`adorsys/keycloak-config-cli:6.5.1-26.5.5`) applies `infrastructure/keycloak/config/corporate-travel.json` on every `up`. All six app services wait for it with `service_completed_successfully`. Keycloak now runs plain `start-dev`.
- `realm-export.json` deleted. It was a database dump that contained the realm's **RSA private keys and HMAC/AES secrets**. The curated definition has no keys (Keycloak generates them), no built-in clients, no service-account users and no password hashes. The old keys remain in git history and in the existing database; they are demo keys only.
- The config-cli build targets Keycloak 26.5.5. Against 26.8.0 it works for everything this realm uses; no compatibility errors were seen.
- Realm fixes made while curating:
  - `employee_id`, `reports_to`, `assists` declared in the user profile. They were previously dropped, so `employee_id` was missing from tokens.
  - Standard scopes `basic`, `profile`, `email`, `roles`, `web-origins`, `acr`, `service_account` defined and assigned. Tokens now carry `email` and `name`. Roles, `sub` and `preferred_username` come from the standard scopes; `user-attributes` keeps only `employee_id` and `tenant_id`.
  - `standard.token.exchange.enabled` is `"true"` on `employee-bff` only and explicitly `"false"` elsewhere. Keycloak 26.8 rejects it on the public `employee-portal`.
  - Users list `default-roles-corporate-travel`, matching what Keycloak assigns to new users. Tokens now also carry `offline_access`, `uma_authorization` and an `account` audience.
- config-cli gotchas found and handled:
  - The `userProfile` block is skipped unless the realm attribute `userProfileEnabled` is set.
  - Attributes merge, so a stale value must be overwritten explicitly, not omitted.
  - Mapper config defaults that Keycloak adds on create (`userinfo.token.claim`) must be declared, or a second apply removes them.
  - Remote state (default) means config-cli only deletes what it created; Keycloak's own default scopes are left alone.
- `seed-data.sh` no longer patches Keycloak via the Admin API. Its premise was wrong: the browser path (portal PKCE token exchanged by `employee-bff`) needs the flag only on the requester, which `validate-realm-config.sh` now proves.
- `scripts/kc-realm-export-test/` renamed to `scripts/keycloak-realm-test/`. `validate-realm-export.sh` renamed to `validate-realm-claims.sh`. New `validate-realm-config.sh` starts a throwaway Keycloak, applies twice, diffs, runs the claim checks and the PKCE exchange check. Added as a CI job.
- Verified:
  - Migrated live realm: apply succeeds, forced re-apply leaves realm and users unchanged.
  - Fresh realm: `validate-realm-config.sh` 8/8 (including 65/65 claim checks).
  - Live stack: `podman compose up -d` orders correctly; seed OK; e2e 71/71; OPA 5/5.
- Not done:
  - Realm split into several files (optional step 8). One 1,180-line file is still readable.
  - The WebAuthn `requireResidentKey` deprecation warning still appears for the migrated realm. The curated config does not set WebAuthn policy, so it doesn't fix stored values. Harmless; a fresh realm uses current defaults.
  - Docs referencing `realm-export.json` and the old script paths (`README.md`, `ADR-IMPLEMENTATION-PLAN.md`, `memory-bank/progress.md`) are left for Phase 7. `README.md` has uncommitted user edits.

### Phase 3 — Backend platform (Java 25, Gradle 9.8, Boot 4.1)

Do this in two steps so failures are easy to attribute.

**Step 3a — toolchain**

1. Gradle wrapper → 9.8.1.
2. Java toolchain → 25 in root and all modules. Dockerfiles → `gradle:9.8-jdk25` / `eclipse-temurin:25-jre` (pick a current Ubuntu base) **[verify tags]**.
3. Stay on Boot 3.5.x latest patch (3.5.16); run tests.

**Step 3b — Boot 4.1.1 / Spring Cloud 2025.1.3**

1. Bump BOMs. Delete the pinned Flyway, Mockito and `opentelemetry-api` versions; let the Boot BOM manage them.
2. Modular starters: add `spring-boot-starter-flyway`; check other auto-configurations that moved into modules (actuator, data, webclient/restclient) **[verify per module]**.
3. Jackson 3 (`tools.jackson` packages): update `domain-models` (`jackson-datatype-jsr310` is built in to Jackson 3) and any custom serializers.
4. springdoc → 3.1.1.
5. Hibernate 7 / JPA: check entity mappings and queries.
6. Spring Data Neo4j 8 + driver 6: check `Neo4jConfig` and repository queries.
7. JUnit 6: confirm test engine; remove the explicit `junit-jupiter` dependency if the starter covers it.
8. WireMock → current 3.x (`org.wiremock:wiremock-standalone`).
9. Clean `security-commons`: stop exporting both `starter-web` and `starter-webflux` as `api`. Remove `jjwt` if unused.
10. Unify Jacoco and wire `jacocoTestCoverageVerification` into `check`, or drop the rule.
11. Use the OpenRewrite Spring Boot 4 migration recipe as a first pass **[verify recipe name]**, then fix by hand.

Exit: all tests and e2e gate pass on Boot 4.1.1 / Java 25.

### Phase 4 — Token handling hardening

1. **Audience validation** in each resource server: accept only tokens whose `aud` contains the service's own client ID. Use `spring.security.oauth2.resourceserver.jwt.audiences`. The gateway accepts tokens audienced for the gateway or the target service — decide and document.
2. Replace `KeycloakTokenExchangeClient` with Spring Security 7 `TokenExchangeOAuth2AuthorizedClientProvider` + `RestClientTokenExchangeTokenResponseClient`. Keep the existing WireMock test semantics.
3. Fix `JwtAuthenticationConverter` to read `act` as an RFC 8693 object (`act.sub`, nested `act` chains). Keep header fallback until Phase 5 lands.
4. Remove the remaining `.block()` calls on WebClient in servlet code; use `RestClient`.

Exit: a token without the right `aud` is rejected (new tests); e2e gate passes.

### Phase 5 — RFC 8693 delegation spike, then adoption

**Spike (time-boxed):**

1. Enable `token-exchange-delegation` on Keycloak 26.8.0 in a throwaway realm.
2. Prove the Dave-for-Carol flow:
   - How Carol's `may_act` is established (admin delegation vs client delegation with consent).
   - Whether the BFF can obtain a token with `sub=carol`, `act.sub=dave` without holding Carol's live token.
   - How FGAP v2 `delegate` / `delegate-members` permissions are configured, and whether config-cli or Terraform can express them.
   - Interaction with our own delegation-service (Neo4j) and consent-service: which system is the source of truth.
3. Write findings into an ADR-004 revision (or a new ADR-024).

**Adoption (only if the spike succeeds):**

1. Enable the feature in compose; add FGAP v2 config to the realm-as-code definition.
2. BFF requests delegated tokens with `actor_token`; services authorize on `sub` (delegator) + `act.sub` (actor).
3. Keep delegation-service and consent-service as the business policy layer (validity window, purpose, scopes); OPA input gains `act`.
4. Remove `X-Delegated-Subject` / `X-Delegation-Id` headers after the e2e gate passes on the token path.
5. Mark clearly in the docs that this depends on a **preview** Keycloak feature.

**If the spike fails:** keep the header model, document why with the concrete blocker, and revisit at the next Keycloak minor.

### Phase 6 — Frontend

1. `next` 16.4.x, `eslint-config-next` 16.4.x, `react` / `react-dom` 19.3.x.
2. `next-auth` 5.0.0-beta.32, matching `@auth/core` (D4).
3. Tailwind 4.3.x, `@tailwindcss/postcss` 4.3.x.
4. ESLint 10 only if `eslint-config-next` supports it; otherwise stay on 9 **[verify]**.
5. TypeScript 6.0.x (D6); `@types/node` ^24; add `"engines": { "node": ">=24" }`.
6. Evaluate Cache Components (default in Next 17) — note only, no adoption in this upgrade.
6a. Fix the four `react-hooks/set-state-in-effect` violations (`admin/bookings`, `admin/expenses`, `expense`, `travel` pages) and restore the rule to `"error"` in `eslint.config.mjs` (downgraded to `"warn"` in Phase 0).
7. Use the BFF-returned `act` / delegation info in `DelegationBanner` and `IdentityContextPanel` if Phase 5 lands.

Exit: `npm run build` and lint clean; manual login, refresh, logout and delegation flows work.

### Phase 7 — Docs and ADR sync

1. Update `README.md`, `GETTING-STARTED.md`, `IMPLEMENTATION.md`, `memory-bank/techContext.md`, `memory-bank/progress.md` with real versions and the realm-as-code workflow.
2. Fix contradictions listed in §2.5.
3. ADR-004: revise per Phase 5.
4. New ADR: realm-as-code tool choice (D1).
5. ADRs 008, 009, 012, 020, 021, 022: change status to "Proposed" or "Deferred" so they match reality, and note the new Keycloak capabilities each could use (§8).

---

## 6. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| keycloak-config-cli lags Keycloak 26.8 | Realm apply fails or silently ignores new attributes | Idempotency + claim smoke test in Phase 2; use the config-cli image built for the newest Keycloak; fallback to Terraform provider (older Keycloak is ruled out by D2) |
| Delegation is a preview feature | Behaviour or API may change in 26.9+ | Spike first; keep header fallback until proven; pin Keycloak patch |
| Boot 4 / Jackson 3 migration breadth | Many small compile and runtime breaks | Toolchain first (3a), then Boot (3b); OpenRewrite first pass; e2e gate |
| Postgres 18 major upgrade | Existing local data lost | Recreate volumes; demo data comes from `seed-data.sh` |
| Neo4j 5 → 2026.x config | Strict config validation stops startup | Rename env vars in the same change as the pin |
| Audience validation | Existing tokens rejected | Land after realm-as-code guarantees correct audience mappers |

---

## 7. Suggested Order and Size

| Phase | Size | Depends on |
|---|---|---|
| 0 Baseline | S | — |
| 1 Infra | S–M | 0 |
| 2 Realm-as-code | M | 1 |
| 3 Backend platform | L | 0 (can run in parallel with 1–2) |
| 4 Token hardening | M | 2, 3 |
| 5 Delegation spike / adoption | M (spike S) | 2, 4 |
| 6 Frontend | S | 0 (independent) |
| 7 Docs | S | all |

---

## 8. Follow-ups (out of scope for this upgrade)

| Item | Keycloak / ecosystem capability |
|---|---|
| ADR-003 multi-tenancy | Organizations (org groups, shared IdPs) instead of groups + attribute |
| ADR-008 brokering | Add a second Keycloak realm or mock IdP; Organizations-linked IdPs |
| ADR-009 workload identity | Federated client auth with Kubernetes ServiceAccount tokens or SPIFFE JWT-SVID (needs Kubernetes) |
| ADR-020 observability | Keycloak built-in OTel tracing + Spring Boot 4 OTel + `grafana/otel-lgtm` in compose |
| ADR-021 secrets | Vault 2.x is BSL-licensed; consider OpenBao (MPL-2.0) for the demo |
| ADR-022 SPI | May be unnecessary if `act` + standard mappers cover the claims |
| Testing | Testcontainers 2 + `testcontainers-keycloak` integration tests for token exchange |
| Hardening | DPoP for the BFF, client secret rotation (26.8) |
| Frontend auth | Re-evaluate Better Auth migration |
