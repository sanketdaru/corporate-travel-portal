# ADR-004: Adopt OAuth 2.0 Token Exchange for Delegated Identity

## Status
Accepted — Updated 2026-03-29 (Standard Token Exchange V2)

## Context
The platform must support scenarios where users act on behalf of others:

- Executive assistants booking travel
- Managers approving employee requests
- AI agents acting for users

Keycloak 24+ deprecates the previous "naked" token exchange mechanism. The **Standard Token Exchange (V2)** based on RFC 8693 is the required approach. Naked token exchange allowed a client to obtain a token for any user by supplying only client credentials and a `requested_subject`, with no proof of the actor's identity — creating an impersonation backdoor with no chain of trust.

## Decision
Delegated identity will be implemented using **OAuth 2.0 Standard Token Exchange V2** (RFC 8693) as provided by Keycloak 24+.

### Chain of Trust Requirement

Every token exchange request **must** include a `subject_token` — an existing, valid access token belonging to the actor (the person initiating the delegation, e.g. Dave). This is the defining security property of Standard Token Exchange V2:

- The `subject_token` proves the actor's identity before any delegation is granted.
- Without a valid `subject_token`, Keycloak rejects the request.
- This eliminates the attack surface of naked exchange, where client credentials alone could produce a token for any user.

### Token Exchange Parameters

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `grant_type` | `urn:ietf:params:oauth:grant-type:token-exchange` | RFC 8693 grant |
| `subject_token` | Actor's current access token (e.g. Dave's token) | **Chain of trust — mandatory** |
| `subject_token_type` | `urn:ietf:params:oauth:token-type:access_token` | Token type declaration |
| `requested_token_type` | `urn:ietf:params:oauth:token-type:access_token` | Output token type |
| `audience` | Target resource server (e.g. `travel-service`) | Scopes the issued token to a specific service |
| `requested_subject` | Delegation target user ID (e.g. Carol's ID) | Impersonation target — requires explicit Client Policy grant |

### Resulting Token Claims

The issued delegation token includes:

- `sub` — delegation subject (Carol)
- `act.sub` — the acting principal (Dave)
- Delegation scope and expiration
- `aud` — scoped to the declared audience

### Enforcement via Client Policies

Token exchange permissions are enforced at the realm level using **Keycloak Client Policies**, not per-client feature flags. Client Policies define:

- Which confidential clients are permitted to perform token exchange (`employee-bff`)
- Which target audiences are valid
- Any additional conditions (e.g. client roles, request origin)

This provides centralized, auditable control over delegation trust boundaries.

## Consequences

### Positive
- Industry standard approach (RFC 8693)
- Strong audit trail — actor identity always present in token
- Clear separation between actor (`act.sub`) and subject (`sub`)
- Chain of trust enforced at protocol level — no naked impersonation
- Client Policies provide centralized, fine-grained access control

### Negative
- Increased token lifecycle complexity
- Requires downstream service enforcement of `act` claim
- Client Policy configuration adds Keycloak admin overhead

## Alternatives Considered

### Naked Token Exchange (rejected — security)
Client credentials + `requested_subject` only, no `subject_token`. Provides no proof of actor identity. Removed as a standard feature in Keycloak 24+. Rejected: creates an impersonation backdoor controllable by any client with valid credentials.

### Session-based impersonation (rejected — auditability)
Context switching via server-side session. No cryptographic chain of custody. Rejected: loses audit trail of who is acting on behalf of whom.

### Static role delegation (rejected — flexibility)
Pre-assigned roles per delegation pair. Does not support dynamic, time-bounded, purpose-scoped delegation. Rejected: insufficient for the executive assistant and AI agent use cases.
