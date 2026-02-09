# ADR-011: Implement Comprehensive Audit and Compliance Ledger

## Status
Accepted

## Context
Travel and expense systems require strict audit tracking.

Delegated and federated identities must be traceable.

## Decision
All identity-sensitive actions will produce audit events including:

- Actor identity
- Subject identity
- Consent reference
- Token metadata
- Purpose

Audit records will be stored in immutable storage.

## Consequences

### Positive
- Strong compliance posture
- Enables forensic analysis

### Negative
- Increased storage overhead
- Requires event standardization
