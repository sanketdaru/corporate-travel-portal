# ADR-017: Adopt API Gateway Pattern

## Status
Accepted

## Context
The system requires:

- Centralized identity enforcement
- Token validation
- Rate limiting
- Traffic routing

## Decision
An API Gateway will be deployed.

Candidate implementations:
- Kong Gateway
OR
- Spring Cloud Gateway

Gateway responsibilities:

- JWT validation
- Request routing
- Threat protection
- Policy enforcement hooks

## Consequences

### Positive
- Centralized security enforcement
- Simplified client interactions
- Supports zero-trust patterns

### Negative
- Gateway becomes critical dependency
- Additional latency

## Alternatives Considered
- Direct client-to-service routing
- Service mesh only
