# ADR-011: Implement Comprehensive Audit and Compliance Ledger

## Status
Accepted — Updated 2026-03-29

## Context
Travel and expense systems require strict audit tracking. Delegated and federated identities must be traceable.

In this platform, identities include both humans and AI agents. A booking may be created by an AI agent acting on behalf of a human under a multi-hop delegation chain. The audit ledger must capture the **full identity chain** at every action, not just the immediate caller.

Because Keycloak Standard Token Exchange V2 does not yet emit the RFC 8693 `act` claim, actor identity is carried in the `X-Actor-Token` and `X-Delegation-Id` request headers validated and threaded by the BFF. Services extract the full chain from these headers and their `SecurityContext`.

## Decision
All identity-sensitive actions will produce audit events capturing the full delegation chain:

| Audit Field | Source | Description |
|-------------|--------|-------------|
| `actor_id` | `SecurityContext.actorId` (from JWT `sub`) | The identity that performed the action (Dave, or AI agent) |
| `subject_id` | `SecurityContext.subjectId` (from `X-Delegated-Subject`) | The human principal on whose behalf the action was taken |
| `delegation_id` | `SecurityContext.delegationId` (from `X-Delegation-Id`) | Links to the full delegation record and chain |
| `consent_id` | Resolved from consent-service during validation | Links to the consent record authorising the action |
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
- Services must read and validate delegation headers — cannot rely on JWT alone for audit completeness
- Increased storage overhead per action
- Requires event schema standardisation across all services
- `X-Actor-Token` header trust boundary must be enforced — only the BFF may set it; API gateway must strip it from external requests
