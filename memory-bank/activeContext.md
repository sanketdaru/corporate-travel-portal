# Active Context

**Last Updated**: 2026-10-08

## Current Work Focus

**Phase**: Platform upgrade complete (phases 0–6); Phase 7 docs and ADR sync in progress on branch `upgrade/phase-7-docs`.

Plan and per-phase results: `docs/UPGRADE-PLAN.md`. Delegation design: ADR-024.

## Recent Changes (Upgrade, 2026-10-08)

| Phase | Result |
|---|---|
| 0 Baseline | GitHub Actions CI; images pinned; `get-token.sh` and `setup-local.sh` fixed; e2e 71/71 baseline |
| 1 Infrastructure | Keycloak 26.5.6 → 26.8.0 (in-place DB migration); `KC_BOOTSTRAP_ADMIN_*`; health on port 9000; Neo4j `server.memory.*`; OPA native arm64; Rego v1 style |
| 2 Realm-as-code | `keycloak-config` one-shot (keycloak-config-cli) applies `infrastructure/keycloak/config/corporate-travel.json` on every `up`; `realm-export.json` deleted; user profile declares `employee_id`, `reports_to`, `assists`; standard scopes restored; Admin API patching removed from `seed-data.sh`; `validate-realm-config.sh` in CI |
| 3 Backend platform | Java 25, Gradle 9.8.1, Boot 4.1.1, Spring Cloud 2025.1.3; Jackson 3 (`tools.jackson`), Flyway 12.4 via BOM, modular starters; fixed Jackson 3 creator-detection and trailing-token breaks |
| 4 Token hardening | `aud` validation in every service; one exchanged token per downstream audience; Spring Security 7 token exchange client; RestClient instead of WebClient + `.block()`; tokens no longer returned by the activate endpoint; BFF passes 4xx through, 5xx → 502 |
| 5 RFC 8693 delegation | Spike, then adoption (ADR-024 option B): grant ceremony via `delegation-grant` client, encrypted offline-token store (schema `bff`, AES-256-GCM), delegated exchange with `actor_token`, FGAP v2 via `keycloak-fgap`, `DelegatedActorFilter`, `X-Delegated-Subject` removed; fixed silent Neo4j write failure (missing `Neo4jTransactionManager`) |
| 6 Frontend | Next 16.4, React 19.3, next-auth 5 beta.32, TS 6.0, Tailwind 4.3; ESLint stays on 9; React Compiler lint violations fixed, not suppressed |
| 7 Docs | README, GETTING-STARTED, IMPLEMENTATION, memory-bank, ADR statuses (in progress) |

Verified at the end of Phase 5: 262 unit tests, `validate-realm-config.sh` 14/14, e2e 73/73, OPA 5/5.

## Next Steps

- Finish Phase 7 and merge the upgrade branches.
- Manual click-through of dashboard, travel, expense, admin and delegation pages (Phase 6 refactors are covered by typecheck/build only).
- Follow-ups from `docs/UPGRADE-PLAN.md` §8: Testcontainers + `testcontainers-keycloak` integration tests, Organizations (ADR-003), OTel (ADR-020), OpenBao (ADR-021), DPoP and client secret rotation.

## Active Decisions

- Delegation identity comes from Keycloak RFC 8693 tokens (`sub` = delegator, `act.sub` = actor), a **preview** feature; delegation-service and consent-service remain the business policy layer.
- Realm changes go into `corporate-travel.json`, never the admin console.
- Tenancy stays on groups + `tenant_id` (Organizations deferred).
- Frontend stays on next-auth 5 beta and TypeScript 6.

## Earlier History (condensed)

- 2026-02 to 2026-04: travel, expense, consent and delegation services, BFF with Standard Token Exchange V2 and header-based delegation (now replaced), audit trail (ADR-011), Flyway (ADR-023), unit-test patterns, e2e script.
- 2026-04-10 to 2026-04-17: Next.js frontend (dashboard, travel, expense, admin, delegation pages).
- Details of those sessions are in git history.
