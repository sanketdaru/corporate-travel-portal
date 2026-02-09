# ADR-001: Adopt Corporate Travel & Expense as Reference Domain

## Status
Accepted

## Context
The system is intended to demonstrate advanced identity and access management concepts including:

- Federated identity
- Delegated identity
- Identity brokering
- Token exchange
- Consent and purpose binding
- Multi-tenant SaaS identity isolation

The chosen domain must naturally require these identity patterns instead of artificially injecting them.

## Decision
We will implement a multi-tenant Corporate Travel & Expense (T&E) platform as the reference application domain.

The platform will support:

- Travel booking
- Expense submission
- Approval workflows
- Delegated booking
- External vendor integrations
- ERP integrations

## Consequences

### Positive
- Naturally demonstrates real enterprise IAM challenges
- Supports workforce, partner, and machine identities
- Provides strong compliance and audit scenarios
- Easily extensible for AI agent delegation

### Negative
- Domain complexity increases implementation effort
- Requires modeling multiple trust boundaries

## Alternatives Considered
- Healthcare coordination platform
- Supply chain collaboration platform
- Education learning management system

T&E was chosen because it provides optimal coverage of delegation patterns.
