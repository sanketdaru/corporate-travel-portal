# ADR-021: Use HashiCorp Vault for Secret Management

## Status
Accepted

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
