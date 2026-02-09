# ADR-022: Use Keycloak SPI for Token Enrichment

## Status
Accepted

## Context
Tokens must include:

- Delegation metadata
- Consent identifiers
- Purpose binding claims

## Decision
Custom Keycloak Service Provider Interfaces will be implemented for token enrichment.

## Consequences

### Positive
- Centralized identity context generation
- Enables advanced identity demonstrations

### Negative
- Requires maintenance across Keycloak upgrades
