# ADR-019: Adopt Open Policy Agent for External Authorization

## Status
Accepted

## Context
Fine-grained authorization requires evaluating:

- Delegation context
- Consent scope
- Purpose binding
- Organizational hierarchy

## Decision
OPA will be used as external policy decision point (PDP).

Policy will be written in Rego.

## Consequences

### Positive
- Highly expressive authorization logic
- Decouples policy from application code
- Enables dynamic policy updates

### Negative
- Policy complexity
- Additional network hop for policy evaluation

## Alternatives Considered
- Keycloak Authorization Services
- AWS Cedar
