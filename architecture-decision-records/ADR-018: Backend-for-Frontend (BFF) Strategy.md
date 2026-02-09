# ADR-018: Backend-for-Frontend (BFF) Strategy

## Status
Accepted

## Context
Different clients require different:

- Token handling approaches
- Aggregation logic
- Authorization contexts

## Decision
Each major client channel will have a dedicated BFF service.

Examples:
- Employee portal BFF
- Admin portal BFF
- Partner portal BFF

## Consequences

### Positive
- Simplified client security logic
- Centralized token exchange handling
- Improved API stability

### Negative
- Additional services to maintain
