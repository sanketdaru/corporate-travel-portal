# ADR-011: Implement Comprehensive Audit and Compliance Ledger

## Status
Accepted — Updated 2026-10-08 (identity chain now from RFC 8693 tokens, ADR-024)

## Context
Travel and expense systems require strict audit tracking. Delegated and federated identities must be traceable.

In this platform, identities include both humans and AI agents. A booking may be created by an AI agent acting on behalf of a human under a multi-hop delegation chain. The audit ledger must capture the **full identity chain** at every action, not just the immediate caller.

Delegated requests carry a Keycloak RFC 8693 token (`sub` = delegator, `act.sub` = actor; ADR-024). Services verify the actor's own token from `X-Actor-Token` (`DelegatedActorFilter`) and read audit metadata from `X-Delegation-Id`, `X-Consent-Id` and `X-Delegation-Purpose`. `security-commons` builds the `SecurityContext` from these.

## Decision
All identity-sensitive actions will produce audit events capturing the full delegation chain:

| Audit Field | Source | Description |
|-------------|--------|-------------|
| `actor_id` | `SecurityContext.actorId` (verified `X-Actor-Token`, matching `act.sub`; else JWT user) | The identity that performed the action (Dave, or AI agent) |
| `subject_id` | `SecurityContext.subjectId` (delegated token's `sub`; else JWT user) | The human principal on whose behalf the action was taken |
| `delegation_id` | `SecurityContext.delegationId` (from `X-Delegation-Id`) | Links to the full delegation record and chain |
| `consent_id` | `SecurityContext.consentId` (from `X-Consent-Id`, set by the BFF after consent-service validation) | Links to the consent record authorising the action |
| `tenant_id` | `SecurityContext.tenantId` (from JWT `tenant_id` claim) | Tenant scope of the action |
| `action` | Service layer constant | Verb: CREATED, UPDATED, DELETED, STATUS_CHANGED |
| `timestamp` | `NOW()` | Wall-clock time of the action |
| `changes` | JSON diff | Before/after state for mutations |

### Multi-Agent Chain Audit

When an AI agent chain is active (e.g. BookingAgent acting under TravelPlannerAgent's delegation to Carol), the audit record captures:

- `actor_id` = BookingAgent's client ID
- `subject_id` = Carol's user ID
- `delegation_id` = the delegation record linking the full chain

The delegation-service graph provides the full chain traversal (`BookingAgent → TravelPlannerAgent → Carol`) for forensic analysis. Each audit record's `delegation_id` is the entry point into that graph.

### Audit Schema (per service)

```sql
-- Example: travel service
CREATE TABLE travel.booking_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID REFERENCES travel.bookings(id),
    action VARCHAR(100) NOT NULL,        -- CREATED, UPDATED, DELETED, STATUS_CHANGED
    actor_id VARCHAR(255) NOT NULL,      -- Who did it (human or AI agent)
    subject_id VARCHAR(255) NOT NULL,    -- On whose behalf (human principal)
    delegation_id UUID,                  -- Link to delegation record (null if no delegation)
    consent_id UUID,                     -- Link to consent record
    tenant_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    changes JSONB                        -- JSON diff of changes
);
```

### Audit Records Stored In
Immutable PostgreSQL tables per service. No updates or deletes permitted on audit tables. Forensic reconstruction uses the delegation-service Neo4j graph for chain traversal.

## Consequences

### Positive
- Full identity chain recorded at every action — supports forensic analysis of AI agent activity
- `delegation_id` links every audit event to the Neo4j graph for chain reconstruction
- Consistent schema across all services via `security-commons` `SecurityContext`
- Actor and subject always explicit — no ambiguity about who acted vs. who was acted for

### Negative
- `delegation_id` and `consent_id` still come from BFF-set headers, not signed claims
- Increased storage overhead per action
- Requires event schema standardisation across all services
- Services must verify `X-Actor-Token` against `act.sub` on every delegated request (done in `DelegatedActorFilter`)
