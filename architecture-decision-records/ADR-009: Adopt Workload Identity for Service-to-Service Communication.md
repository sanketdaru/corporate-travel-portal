# ADR-009: Adopt Workload Identity for Service-to-Service Communication

## Status
Proposed — partly implemented (as of 2026-10-12): client credentials for the one internal call

### Implementation status / current options
Confidential clients authenticate to Keycloak with client secrets (`client-secret` authenticator, secrets from `.env`). There is no mTLS between services. Calls from `employee-bff` carry the user's token or a token-exchange result. Token exchange for delegation is implemented (ADR-004, ADR-024).

The one internal call, expense-service → travel-service budget lookup (`TravelServiceClient`), uses OAuth client credentials: expense-service gets a service-account token (`aud: travel-service`, realm role `budget_reader`) through Spring Security's `OAuth2ClientHttpRequestInterceptor` and caches it until expiry. travel-service accepts `/api/bookings/{id}/budget` only with role `budget_reader`, and only for the tenant in the `tenantId` query parameter. If the budget cannot be read, submission fails with 503 instead of skipping the check.

Not implemented: mTLS and secretless workload identity.

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
