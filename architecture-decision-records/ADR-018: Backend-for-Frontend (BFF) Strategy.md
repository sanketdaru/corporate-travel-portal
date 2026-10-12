# ADR-018: Backend-for-Frontend (BFF) Strategy

## Status
Accepted — Updated 2026-10-08 (RFC 8693 delegation, ADR-024)

## Context
Different clients require different token handling, aggregation logic, and authorization contexts.

More specifically, the Employee BFF is the **delegation gateway** for the platform. It is the only component that holds the actor's full token and is trusted to:

1. Perform OAuth 2.0 token exchange (RFC 8693, with `actor_token` when delegating) to obtain audience-scoped tokens
2. Validate active delegation relationships against the delegation-service
3. Hold the delegator's encrypted offline token (grant store) and forward the actor's token for verification
4. Support **multi-agent AI chains** — where an AI agent client authenticates and acts on behalf of a human under explicit, consent-backed delegation

## Decision
Each major client channel has a dedicated BFF service.

Examples:
- Employee portal BFF (`employee-bff`) — human users and AI agent clients
- Admin portal BFF — admin operations
- Partner portal BFF — external partner integrations

### Employee BFF Responsibilities

**1. Authentication Entry Point**
- Accepts the actor's access token (from browser login or AI agent authentication)
- Validates token with Keycloak

**2. Delegation Context Resolution**
Before any delegated call, the BFF:
1. Receives a `delegationId` from the caller (e.g. "act as Carol under delegation X")
2. Fetches the delegation record from the delegation-service and validates it is active
3. Validates consent exists for the requested action/scope via the consent-service
4. Verifies the caller is the delegate and that a Keycloak grant exists (409 otherwise)
5. Stores the resolved `DelegationContext` (with per-audience tokens) in the server-side session

**3. Delegated Token Exchange (RFC 8693, ADR-024)**
On activation, once per downstream audience (`delegation.audiences`, default travel + expense):
- `subject_token` = delegator's (Carol's) access token, refreshed from the offline token stored at grant time
- `actor_token` = actor's (Dave's) access token
- `audience` = target service client ID (e.g. `travel-service`)
- Result: token with `sub` = Carol, `act.sub` = Dave, `aud` = one service. Tokens stay in the server-side session.

**4. Delegation Headers**
Every delegated downstream call carries:

```
Authorization: Bearer <delegated token>         # sub = delegator, act.sub = actor
X-Actor-Token: <actor's own token>              # verified by the service against act.sub
X-Delegation-Id: <delegation-record-uuid>       # audit metadata
X-Consent-Id, X-Delegation-Purpose              # audit metadata, when known
```

`X-Delegated-Subject` is no longer sent or read.

**5. Multi-Agent Chain Support**
An AI agent client (registered as a confidential Keycloak client) follows the same flow as a human actor. The BFF does not distinguish between human and agent callers — the delegation-service graph models the chain:

```
Carol (User) --[CAN_ACT_AS]--> TravelPlannerAgent (Agent)
TravelPlannerAgent (Agent) --[CAN_ACT_AS]--> BookingAgent (Agent)
```

Each agent in the chain has its own Keycloak identity and its own delegation record. The graph traversal in the delegation-service validates the full chain at each hop. (Agent chains are modelled in delegation-service; the RFC 8693 token path is exercised for human delegation only.)

### BFF API Surface

```
# Delegation grant and context (login is handled by the frontend via Keycloak PKCE)
POST   /api/bff/delegation/{id}/grant         - Delegator starts the Keycloak grant (consent, offline token)
GET    /api/bff/delegation/grant/callback     - Grant callback; stores the encrypted offline token
GET    /api/bff/delegation/{id}/grant         - Grant status
DELETE /api/bff/delegation/{id}               - Revoke (delegator only; revokes the offline session)
POST   /api/bff/delegation/activate/{id}      - Activate a delegation context (delegate only)
DELETE /api/bff/delegation/deactivate         - Exit delegation mode
GET    /api/bff/delegation/context            - Get current active context

# Aggregated Domain APIs (auto-delegation when context is active)
GET    /api/bff/bookings                      - List bookings
POST   /api/bff/bookings                      - Create booking
GET    /api/bff/expenses                      - List expenses
POST   /api/bff/expenses                      - Create expense
GET    /api/bff/dashboard                     - Aggregated dashboard
```

## Consequences

### Positive
- Single point of responsibility for delegation context validation
- Downstream services get the subject from a Keycloak-signed token — no need to re-resolve delegation records or trust a subject header
- AI agent clients use identical flow to human users — no special-casing
- BFF failure surface is isolated — if delegation is misconfigured, it fails at the gateway, not deep in a service call

### Negative
- The BFF stores long-lived delegator credentials (encrypted offline tokens); the encryption key must be protected
- Depends on a Keycloak preview feature (`token-exchange-delegation`)
- Additional latency from delegation-service and consent-service lookups on each delegated request
- Additional services to maintain per client channel
