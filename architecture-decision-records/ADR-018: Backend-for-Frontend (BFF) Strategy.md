# ADR-018: Backend-for-Frontend (BFF) Strategy

## Status
Accepted — Updated 2026-03-29

## Context
Different clients require different token handling, aggregation logic, and authorization contexts.

More specifically, the Employee BFF is the **delegation gateway** for the platform. It is the only component that holds the actor's full token and is trusted to:

1. Perform OAuth 2.0 Standard Token Exchange V2 to obtain audience-scoped tokens
2. Validate active delegation relationships against the delegation-service
3. Thread the full delegation identity context to downstream services as validated headers
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
4. Caches the resolved `DelegationContext` for the request lifetime

**3. Token Exchange (Standard V2)**
For each downstream service call, the BFF performs a Keycloak Standard Token Exchange V2:
- `subject_token` = actor's current access token (chain of trust, mandatory)
- `audience` = target service client ID (e.g. `travel-service`)
- Result: audience-scoped token where `sub` = actor (Dave or AI agent)

**4. Delegation Header Threading**
Every downstream call carries the full delegation context as validated headers:

```
Authorization: Bearer <audience-scoped token>   # actor identity + auth proof
X-Delegated-Subject: <carol-user-id>             # human principal being acted for
X-Delegation-Id: <delegation-record-uuid>        # validated delegation record
X-Actor-Token: <actor-original-token>            # for audit chain reconstruction
```

**5. Multi-Agent Chain Support**
An AI agent client (registered as a confidential Keycloak client) follows the same flow as a human actor. The BFF does not distinguish between human and agent callers — the delegation-service graph models the chain:

```
Carol (User) --[CAN_ACT_AS]--> TravelPlannerAgent (Agent)
TravelPlannerAgent (Agent) --[CAN_ACT_AS]--> BookingAgent (Agent)
```

Each agent in the chain has its own Keycloak identity, its own audience-scoped token, and its own delegation record. The graph traversal in the delegation-service validates the full chain at each hop.

### BFF API Surface

```
# Authentication & Delegation Context
POST   /api/bff/auth/login                    - Human login, returns session token
POST   /api/bff/delegation/activate/{id}      - Activate a delegation context
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
- Downstream services receive pre-validated, trusted headers — no need to re-resolve delegation records
- AI agent clients use identical flow to human users — no special-casing
- BFF failure surface is isolated — if delegation is misconfigured, it fails at the gateway, not deep in a service call
- Forward-compatible: when Keycloak ships `act` claim support, header threading is replaced by JWT claims transparently

### Negative
- BFF is a trust boundary — downstream services must not accept `X-Delegated-Subject` from external callers directly
- Additional latency from delegation-service and consent-service lookups on each delegated request
- Additional services to maintain per client channel
