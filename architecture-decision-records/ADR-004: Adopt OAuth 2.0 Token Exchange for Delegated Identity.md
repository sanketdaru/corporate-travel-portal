# ADR-004: Adopt OAuth 2.0 Token Exchange for Delegated Identity

## Status
Accepted — Updated 2026-03-29 (Standard Token Exchange V2 + Application-Level Delegation Chain)

## Context
The platform must support scenarios where one identity acts on behalf of another:

- Executive assistants booking travel on behalf of executives
- Managers approving employee requests
- **AI agents acting autonomously on behalf of users** — the primary driver of this PoC

The deeper objective of this platform is to demonstrate how **standard delegation constructs can be extended to support multi-agent agentic AI system interactions within an enterprise**: an AI agent has its own identity, receives bounded authority from a human via consent, and can compose with other agents — all with a full, auditable delegation chain.

Keycloak 24+ deprecates the previous "naked" token exchange mechanism. The **Standard Token Exchange V2** based on RFC 8693 is the recommended approach for internal-to-internal token exchange. However, as of Keycloak 26, Standard V2 supports **audience scoping only** — it does not yet support `requested_subject` (impersonation) or the RFC 8693 `act` claim (delegation chain).

This gap is intentional design space for the PoC: we build the delegation identity chain at the application layer, making it explicit and inspectable at every service boundary. When Keycloak ships native `act` claim support, the application-layer headers become a drop-in replacement.

## Decision

Delegated identity will be implemented using a **two-layer approach**:

### Layer 1 — Keycloak Standard Token Exchange V2 (RFC 8693) for Audience Scoping

The actor (Dave, or an AI agent) authenticates and holds their own access token. When they need to call a downstream service, the BFF performs a V2 token exchange to obtain an **audience-scoped token** targeting that specific resource server. The actor's identity (`sub`) is preserved in the exchanged token.

**No `requested_subject` is used.** Standard V2 does not support impersonation. The actor always remains the JWT subject.

### Layer 2 — Application-Level Delegation Context for Identity Chain

The delegation target (Carol) and the delegation chain are threaded as explicit, validated HTTP headers from the BFF to every downstream service call:

| Header | Value | Purpose |
|--------|-------|---------|
| `Authorization` | Actor's audience-scoped token | Actor identity (`sub`) + proof of authentication |
| `X-Delegated-Subject` | Carol's user ID | The human whose authority is being exercised |
| `X-Delegation-Id` | UUID of the delegation record | Links to delegation-service for validation |
| `X-Actor-Token` | Actor's original token (Dave/Agent) | Carried for audit logging of the full chain |

### Token Exchange Parameters (Layer 1)

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `grant_type` | `urn:ietf:params:oauth:grant-type:token-exchange` | RFC 8693 grant |
| `subject_token` | Actor's current access token | **Chain of trust — mandatory** |
| `subject_token_type` | `urn:ietf:params:oauth:token-type:access_token` | Token type declaration |
| `requested_token_type` | `urn:ietf:params:oauth:token-type:access_token` | Output token type |
| `audience` | Target resource server (e.g. `travel-service`) | Scopes the issued token to a specific service |

### Resulting Token Claims

The issued audience-scoped token contains:

- `sub` — the actor (Dave or AI agent)
- `aud` — scoped to the declared target service
- `azp` — `employee-bff` (the requester)

The delegation context (Carol's identity, delegation record, consent) is carried in the application-layer headers, not in the JWT.

### Multi-Agent Delegation Chain

For AI agent chains (e.g. TravelPlannerAgent orchestrating BookingAgent on Carol's behalf):

```
Carol grants consent → TravelPlannerAgent (Keycloak client)
    ↓
TravelPlannerAgent authenticates with its own client identity
    ↓
BFF: V2 token exchange → aud=travel-service token (sub=travel-planner-agent)
    ↓
Headers: X-Delegated-Subject=carol, X-Delegation-Id=<id>, X-Actor-Token=<agent-token>
    ↓
TravelPlannerAgent orchestrates BookingAgent
    ↓
BFF: V2 token exchange → aud=travel-service token (sub=booking-agent)
    ↓
Headers: X-Delegated-Subject=carol, X-Delegation-Id=<id>, X-Actor-Token=<booking-agent-token>
    ↓
travel-service: validates delegation chain in Neo4j graph, validates consent, audits full chain
```

Every identity in the chain is preserved. The delegation-service graph models the full depth. Consent is validated at each service boundary.

### Downstream Service Enforcement

Every service receiving a delegated call must:

1. Extract `sub` from the JWT (actor identity)
2. Read `X-Delegated-Subject` header (the human principal)
3. Read `X-Delegation-Id` header and validate the delegation record against the delegation-service
4. Validate consent via the consent-service for the requested action
5. Build a `SecurityContext` with `actorId`, `subjectId`, `delegationId`, `tenantId`
6. Pass the full context to OPA for authorization
7. Record `actorId` and `subjectId` in audit tables

### Forward Compatibility

This design is structured to adopt the RFC 8693 `act` claim when Keycloak ships it. At that point:

- `X-Actor-Token` header → `act.sub` JWT claim
- Application-layer chain validation → JWT-level chain validation
- The delegation-service and consent-service validation remain unchanged

The `SecurityContext` abstraction in `security-commons` already reads both JWT claims and headers, making the transition transparent to individual services.

### Keycloak Configuration

Token exchange is enabled at the feature level: `KC_FEATURES=token-exchange-standard`. The **Standard token exchange** toggle must be enabled on the `employee-bff` client in the Keycloak Admin Console. No Fine-Grained Admin Permissions and no Client Policies are required for Standard V2.

## Consequences

### Positive
- Industry standard approach (RFC 8693) for authentication layer
- Actor identity always preserved in token — no impersonation, no identity loss
- Full delegation chain auditable at every service boundary
- Composable for arbitrary-depth multi-agent chains
- Forward-compatible with RFC 8693 `act` claim
- Consent enforcement at each service hop, not just at exchange time
- No dependency on Keycloak FGAP (which is V1-only and a preview feature)

### Negative
- Delegation context carried in headers requires every service to validate them
- Headers can be forged if not validated — validation against delegation-service is mandatory, not optional
- More moving parts than pure JWT-based delegation
- `act` claim not yet in token — downstream services must read headers

## Alternatives Considered

### Naked Token Exchange (rejected — security)
Client credentials + `requested_subject` only, no `subject_token`. Provides no proof of actor identity. Removed as a standard feature in Keycloak 24+. Rejected: creates an impersonation backdoor controllable by any client with valid credentials.

### Legacy Token Exchange V1 with `requested_subject` (rejected — wrong identity model for agents)
V1 impersonation produces `sub=Carol` with no actor trace. The AI agent disappears from the identity chain. Rejected: incompatible with the multi-agent objective where every agent must have its own auditable identity. Also requires FGAP (preview feature) and produces tokens where the agent's identity is lost.

### Session-based impersonation (rejected — auditability)
Context switching via server-side session. No cryptographic chain of custody. Rejected: loses audit trail of who is acting on behalf of whom.

### Static role delegation (rejected — flexibility)
Pre-assigned roles per delegation pair. Does not support dynamic, time-bounded, purpose-scoped delegation. Rejected: insufficient for the executive assistant and AI agent use cases.

Read more at: https://www.keycloak.org/securing-apps/token-exchange
