# ADR-007: Use External Policy Engine for Fine-Grained Authorization

## Status
Accepted

## Context
Authorization requires evaluation of:

- Delegation relationships
- Consent constraints
- Organizational hierarchies
- Purpose-based access

## Decision
Fine-grained authorization will be handled using an external policy engine (OPA or Cedar).

Keycloak will handle authentication and coarse RBAC.

## Consequences

### Positive
- Clear separation of concerns
- Highly flexible policy management
- Supports ABAC and contextual authorization

### Negative
- Additional infrastructure
- Increased request latency

## Alternatives Considered
- Keycloak Authorization Services only
- Hard-coded service authorization

External policy engine selected for scalability and teaching value.
