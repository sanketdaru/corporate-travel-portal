# ADR-012: Use Kubernetes-Native Deployment Model

## Status
Proposed — not implemented (as of 2026-10-08)

### Implementation status / current options
Not implemented. The platform runs locally with `podman compose` (`docker-compose.yml`); there are no Helm charts, Kubernetes manifests or service mesh configuration. Moving to Kubernetes is the prerequisite for ADR-009 workload identity (Keycloak federated client authentication with ServiceAccount tokens).

## Context
The platform must demonstrate cloud-native identity deployment patterns.

## Decision
All services including Keycloak will be deployed on Kubernetes using:

- Helm charts
- Service mesh integration (optional)
- Sidecar policy enforcement

## Consequences

### Positive
- Demonstrates modern enterprise deployment
- Enables workload identity integration

### Negative
- Operational complexity
- Requires Kubernetes expertise
