# ADR-008: Implement Identity Brokering for Enterprise Federation

## Status
Accepted

## Context
Enterprise tenants may use their own workforce identity providers.

The system must support:

- BYOI onboarding
- Attribute mapping
- Just-in-time provisioning

## Decision
Keycloak will act as an identity broker connecting to tenant IdPs using:

- OIDC
- SAML

## Consequences

### Positive
- Enables enterprise federation
- Supports heterogeneous identity ecosystems

### Negative
- Requires attribute mapping maintenance
- Federation debugging complexity

## Alternatives Considered
- Local identity only
- Custom federation gateway

Keycloak brokering selected for standards alignment.
