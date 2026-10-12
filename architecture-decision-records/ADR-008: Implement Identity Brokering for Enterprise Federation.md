# ADR-008: Implement Identity Brokering for Enterprise Federation

## Status
Proposed — not implemented (as of 2026-10-08)

### Implementation status / current options
Nothing is implemented: the realm defines no identity providers, and all users are local Keycloak users. A demo would need a second Keycloak realm or a mock OIDC/SAML IdP as the "tenant IdP". Keycloak Organizations (supported; organization-linked and, since 26.8, shared IdPs) is the natural way to attach IdPs per tenant, but it is tied to the deferred ADR-003 Organizations move (`docs/UPGRADE-PLAN.md` D7, §8).

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
