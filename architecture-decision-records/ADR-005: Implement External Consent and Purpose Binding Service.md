# ADR-005: Implement External Consent and Purpose Binding Service

## Status
Accepted

## Context
Delegated access requires explicit user consent and purpose limitation.

Keycloak provides basic consent but lacks advanced purpose binding and lifecycle management.

## Decision
A dedicated Consent Service will be implemented.

Responsibilities:

- Capture user consent
- Bind consent to purpose and scope
- Store consent ledger
- Inject consent metadata into tokens
- Validate consent during access enforcement

## Consequences

### Positive
- Enables fine-grained delegation
- Improves compliance and auditability
- Supports regulatory use cases

### Negative
- Introduces additional service complexity
- Requires token enrichment integration

## Alternatives Considered
- Using Keycloak native consent
- Embedding consent in domain services

External service chosen to improve extensibility and policy independence.
