# ADR-009: Adopt Workload Identity for Service-to-Service Communication

## Status
Accepted

## Context
Microservices and integrations require secure machine authentication.

## Decision
Service-to-service communication will use:

- OAuth client credentials
- Token exchange for downstream delegation
- mTLS for internal communication

## Consequences

### Positive
- Eliminates shared secrets
- Enables traceable service actions
- Supports zero-trust architecture

### Negative
- Certificate lifecycle management required
- Token exchange increases complexity

## Alternatives Considered
- Static API keys
- Network trust-based authentication
