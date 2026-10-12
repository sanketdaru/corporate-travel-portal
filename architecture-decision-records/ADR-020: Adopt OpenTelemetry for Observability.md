# ADR-020: Adopt OpenTelemetry for Observability

## Status
Proposed — not implemented (as of 2026-10-08)

### Implementation status / current options
Not implemented. No service depends on OpenTelemetry or Micrometer Tracing (the unused `opentelemetry-api` BOM entry was removed in the Spring Boot 4 upgrade), and the compose stack has no collector or backend. Audit correlation currently relies on `delegation_id` in the audit tables (ADR-011).

Current options: Keycloak's built-in OpenTelemetry tracing (supported since 26.1), Spring Boot 4 OpenTelemetry support for the services, and a single `grafana/otel-lgtm` container in compose as the backend (`docs/UPGRADE-PLAN.md` §8).

## Context
Identity-driven systems require:

- End-to-end traceability
- Token propagation tracing
- Audit correlation

## Decision
OpenTelemetry will be used for:

- Distributed tracing
- Metrics
- Log correlation

## Consequences

### Positive
- Unified observability standard
- Strong debugging support

### Negative
- Instrumentation effort
