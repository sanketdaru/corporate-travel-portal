# ADR-006: Use Microservices Architecture with Backend-for-Frontend Pattern

## Status
Accepted

## Context
The system includes multiple client types:

- Web portal
- Mobile app
- Partner portal

Different clients require customized identity and token flows.

## Decision
The system will adopt:

- Domain-driven microservices
- Backend-for-Frontend (BFF) layer per client

## Consequences

### Positive
- Improved client-specific security enforcement
- Simplified token management
- Reduced client complexity

### Negative
- Additional service layer
- Increased deployment footprint

## Alternatives Considered
- Direct client-to-microservice access
- Monolithic backend

BFF chosen for improved security control.
