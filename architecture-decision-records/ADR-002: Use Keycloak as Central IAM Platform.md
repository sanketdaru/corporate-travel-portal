# ADR-002: Use Keycloak as Central IAM Platform

## Status
Accepted

## Context
The system requires:

- Open standards support (OIDC, OAuth 2.1, SAML)
- Identity brokering
- Token exchange
- Authorization services
- Extensibility via plugins
- Multi-tenant support

## Decision
Keycloak will be used as the primary Identity and Access Management platform.

Keycloak responsibilities:

- Authentication
- Federation broker
- Token issuance
- Authorization server
- Consent integration
- Token exchange implementation

## Consequences

### Positive
- Mature open-source IAM
- Supports required protocols
- SPI extensibility
- Strong ecosystem

### Negative
- Operational overhead
- Complex configuration for advanced scenarios

## Alternatives Considered
- Auth0
- Okta
- Azure AD B2C
- Spring Authorization Server

Keycloak selected due to extensibility and self-hosting capability.
