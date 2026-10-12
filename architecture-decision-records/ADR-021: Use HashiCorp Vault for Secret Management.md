# ADR-021: Use HashiCorp Vault for Secret Management

## Status
Proposed — not implemented (as of 2026-10-08)

### Implementation status / current options
Not implemented. Secrets are plain values: client secrets in `.env` / `application.yml` (`*-secret-change-in-production`), database passwords in compose, and the BFF's AES-256-GCM key for stored offline tokens (`BFF_GRANT_ENCRYPTION_KEY`, with a committed demo default; ADR-024). That key is the most obvious first candidate for a secret manager.

Licensing note: HashiCorp Vault is under the Business Source License (BSL); OpenBao (MPL-2.0 fork) is the open-source alternative for this demo.

## Context
The system handles sensitive credentials including:

- Federation trust keys
- Service credentials
- Certificate lifecycle

## Decision
Vault will manage:

- Secrets
- Certificates
- Encryption keys

## Consequences

### Positive
- Centralized secret governance
- Dynamic credential generation

### Negative
- Additional infrastructure
- Requires integration across services
