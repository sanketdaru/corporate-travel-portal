# ADR-022: Use Keycloak SPI for Token Enrichment

## Status
Proposed — not implemented (as of 2026-10-08)

### Implementation status / current options
Not implemented; there is no custom Keycloak SPI. Delegation metadata now comes from Keycloak's native RFC 8693 delegation (`act` claim, `may_act`; ADR-024), and the remaining claims (`tenant_id`, `employee_id`, audiences) come from standard protocol mappers. Consent and purpose identifiers travel as audit headers (`X-Consent-Id`, `X-Delegation-Purpose`). A custom SPI may therefore be unnecessary; revisit only if consent or purpose must be signed into the token.

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
