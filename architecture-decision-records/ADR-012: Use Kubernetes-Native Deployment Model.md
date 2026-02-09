# ADR-012: Use Kubernetes-Native Deployment Model

## Status
Accepted

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
