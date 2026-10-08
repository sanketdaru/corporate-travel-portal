# ADR-024: Adopt Keycloak Native RFC 8693 Delegation

## Status
Accepted (2026-10-08) — option B adopted and implemented. Supersedes the header-based delegation identity of ADR-004.

Relates to: ADR-004 (token exchange for delegated identity), ADR-005 (consent service), ADR-021 (Vault)

## Context

ADR-004 carries the delegation subject in application headers (`X-Delegated-Subject`, `X-Delegation-Id`, `X-Actor-Token`) because Keycloak's Standard Token Exchange V2 could only scope audiences: it could not issue a token whose subject is the delegator and whose `act` claim names the actor.

Keycloak 26.7 added RFC 8693 delegation (`actor_token`, `may_act`, `act`), promoted to **preview** in 26.8 (`token-exchange-delegation`). This ADR records what a spike against Keycloak 26.8.0 found, and what adopting it would cost.

Spike: `scripts/spikes/keycloak-delegation-spike.sh` (throwaway Keycloak, realm-as-code definition plus delegation settings).

## How Keycloak delegation works

1. The subject (Carol) logs in through a client that requests the parameterized scope `delegation:user:<actor>` (here `delegation:user:dave.assistant`).
2. Keycloak checks an FGAP v2 permission granting the actor the `delegate` scope on the subject (or `delegate-members` on a group). Without it, the scope is silently dropped.
3. The client must have **Consent required**. Carol sees "Delegate token to administrator dave" and approves. Consent is not stored: it is asked on every login.
4. Carol's token carries `may_act: {"sub": "<dave-uuid>"}`.
5. A confidential client entitled to both tokens (here `employee-bff`) exchanges `subject_token` = Carol's token and `actor_token` = Dave's token.
6. The issued token has `sub` = Carol, `act: {"sub": "<dave-uuid>"}`, no refresh token, no session id.

## Spike results (Keycloak 26.8.0)

| # | Question | Result |
|---|---|---|
| Q1 | `may_act` after Carol's delegation-scope login | Yes, after a consent screen |
| Q2 | Delegated exchange via `employee-bff` | Works: `sub`=Carol, `act.sub`=Dave's UUID, 300 s, no refresh token |
| Q3 | `audience=travel-service` on the delegated exchange | Works (not listed in the documented parameter table) |
| Q4 | Standard exchange with a `may_act` token | Rejected: "Subject token with a delegation claim is not allowed for standard token exchange" |
| Q5 | Login without the delegation scope | No `may_act` |
| Q6 | `may_act` after token refresh | Preserved |
| Q7 | Exchange after Carol's SSO sessions end | Fails ("Invalid token") — delegation is bound to the subject's session |
| Q8 | Subject without a matching FGAP permission | Scope dropped, no `may_act`, login succeeds |
| Q9 | Offline token (`offline_access delegation:user:dave.assistant`) after Carol logs out | Refresh keeps `may_act`; exchange succeeds |

Additional findings:

- `delegation:user` / `delegation:client` are created as realm default optional scopes, but clients whose scope lists are managed explicitly (our realm-as-code) do **not** inherit them; they must be attached per client.
- The consent screen is a required action (`execution=OAUTH_GRANT`) — any client with `consentRequired` shows it on every login.
- `act` contains only the actor's Keycloak user id (UUID). This platform identifies users by `preferred_username` in records, OPA input and audit tables.

## The gap

This platform's delegation is asynchronous: Carol grants Dave a delegation that lasts days (validity window, purpose, scopes in delegation-service and consent-service), and Dave acts while Carol is offline.

Keycloak delegation needs **Carol's token at exchange time**. With normal tokens it ends with Carol's SSO session (idle 30 min, max 10 h in this realm) — Q7. Only an **offline token** bridges the gap — Q9.

## Options

### A. Keep the header model (ADR-004) — status quo after the Phase 4 hardening

- Tokens are audience-scoped per service; `aud` is validated everywhere; the BFF never exposes tokens.
- Downstream services trust `X-Delegated-Subject` from callers holding a valid audience-scoped token, after delegation-service and consent-service checks in the BFF.
- No preview features.

### B. Adopt Keycloak delegation with offline subject tokens — **chosen**

Required changes:

1. **Grant ceremony.** When Carol grants a delegation, the BFF runs an authorization-code flow for Carol through a dedicated confidential client (e.g. `delegation-grant`, `consentRequired=true`, `delegation:user` and `offline_access` attached, `employee-bff` audience) with `scope=openid offline_access delegation:user:<delegate>`. A dedicated client avoids a consent screen on every normal portal login.
2. **Credential storage.** The BFF stores Carol's **offline refresh token** per delegation, encrypted at rest (ADR-021 Vault or equivalent). That token can mint Carol's own access tokens — it is the most sensitive credential in the system.
3. **Exchange.** On activation, the BFF refreshes Carol's offline token and exchanges it with Dave's token as `actor_token`, per audience.
4. **Revocation.** Revoking or expiring a delegation must revoke Carol's offline session (and consent). Offline sessions expire after 30 days idle in this realm.
5. **Realm-as-code.** Features `token-exchange-delegation,parameterized-scopes`; `adminPermissionsEnabled`; FGAP v2 `delegate` permissions — per delegator/delegate pair, or by group (`delegate-members`). The permission set then duplicates what delegation-service already stores.
6. **Identity mapping.** Services must map `act.sub` (UUID) to the platform's username identifiers, or the platform must move to UUIDs.
7. **Preview.** The feature can change in later 26.x releases.

Benefits: downstream tokens become self-describing (`sub` = subject, `act` = actor), so services no longer trust headers for the subject; Keycloak enforces who may delegate to whom.

### C. Adopt only for interactive (synchronous) delegation

Use Keycloak delegation when the subject is present (e.g. AI-agent actions approved in-session, `delegation:client:<agent>`), keep the header model for long-lived human delegation. Smaller change, demonstrates the feature, but two delegation paths.

## Decision

**Option B: adopt Keycloak delegation with offline subject tokens.** (The spike's recommendation was A; the project chose B.)

Design choices:

| Concern | Decision |
|---|---|
| Grant ceremony | Dedicated confidential client `delegation-grant` (`consentRequired`, PKCE, `offline_access` + `delegation:user` optional scopes, `employee-bff` audience). The BFF runs the delegator's authorization-code flow; normal portal logins show no consent screen. |
| Offline token storage | BFF Postgres schema `bff`, table `delegation_grant`, AES-256-GCM with the delegation id as AAD; key from `delegation.grant.encryption-key` (env `BFF_GRANT_ENCRYPTION_KEY`). Vault/OpenBao can replace the key source later (ADR-021). |
| Who may delegate to whom (FGAP v2) | Static, per tenant: Groups resource type, `delegate-members` scope, policy "members of the same tenant group". Keycloak enforces the tenant boundary; delegation-service and consent-service decide the pair, purpose and window. Applied by `infrastructure/keycloak/fgap/apply-delegation-permissions.sh` (kcadm.sh) because keycloak-config-cli cannot manage the system `admin-permissions` client. |
| Actor identity (`act.sub` is a UUID) | Services verify the forwarded `X-Actor-Token` with their own `JwtDecoder` and require `sub == act.sub`, same tenant, and no nested `act` (`DelegatedActorFilter`). |
| Authorization semantics | The security context of a delegated request is the **actor's** (user id, roles, attributes from the verified actor token); `subjectId` is the delegator from the token's `sub`. Delegated tokens get no Spring authorities. Delegation never grants the delegator's roles. |
| Revocation | `DELETE /api/bff/delegation/{id}`: delegation-service revoke (delegator only), RFC 7009 revocation of the offline token, row deleted. Backstop at activation: inactive delegation or `invalid_grant` on refresh removes the grant. |
| Headers | `X-Delegated-Subject` is no longer sent or read. `X-Delegation-Id`, `X-Consent-Id`, `X-Delegation-Purpose` remain audit metadata. |

Flow:

1. Carol creates the delegation and consent (unchanged), then the frontend sends her to `POST /api/bff/delegation/{id}/grant` → Keycloak (`scope=openid offline_access delegation:user:<delegate>`, `login_hint`) → consent screen → `/api/bff/delegation/grant/callback` (through the Next.js `/api/bff` rewrite). The BFF checks the signed-in user is the delegator and that `may_act` is present, then stores the encrypted offline token.
2. Dave activates: the BFF verifies he is the delegate, validates consent, refreshes Carol's offline token, and exchanges it with Dave's token as `actor_token`, once per audience. Issued tokens: `sub`=Carol, `act.sub`=Dave, `aud`=one service.
3. Services authorize Dave (actor) acting for Carol (subject) via OPA, exactly as before, but the subject now comes from a Keycloak-signed token.

## Consequences

- Keycloak runs with preview features `token-exchange-delegation,parameterized-scopes`; behaviour may change in later 26.x releases. `validate-realm-config.sh` covers grant, delegated exchange, tenant boundary and revocation in CI.
- Keycloak creates the `delegation:*` scopes only for realms created after the feature is enabled; the realm-as-code definition declares `delegation:user` explicitly.
- The BFF now holds long-lived delegator credentials (encrypted). Protecting the encryption key is a deployment concern; the committed key is a demo key.
- Keycloak's consent text reads "Delegate token to administrator <name>" (its built-in message for `delegation:user`).
- Offline sessions expire after 30 days idle (realm default); the delegator must re-authorize after that or after revocation.
- The spike script stays in the repository so the evaluation can be repeated on later Keycloak releases.
