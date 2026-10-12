# ADR-009: Adopt Workload Identity for Service-to-Service Communication

## Status
Proposed — not implemented (as of 2026-10-08)

### Implementation status / current options
Not implemented. Confidential clients authenticate to Keycloak with client secrets (`client-secret` authenticator, secrets from `.env`). There is no mTLS between services, and no service uses client-credentials tokens. Calls from `employee-bff` carry the user's token or a token-exchange result. The one internal call, expense-service → travel-service budget lookup (`TravelServiceClient`), sends no token at all and relies on network trust. Token exchange for delegation is implemented (ADR-004, ADR-024).

Current options for secretless workload identity: Keycloak 26.6+ federated client authentication with Kubernetes ServiceAccount tokens (supported), or SPIFFE JWT-SVID client authentication (preview). Both need a Kubernetes or SPIFFE environment (ADR-012); the demo runs on podman compose.

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
