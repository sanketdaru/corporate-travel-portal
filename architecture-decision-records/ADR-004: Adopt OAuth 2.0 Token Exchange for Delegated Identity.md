# ADR-004: Adopt OAuth 2.0 Token Exchange for Delegated Identity

## Status
Accepted

## Context
The platform must support scenarios where users act on behalf of others:

- Executive assistants booking travel
- Managers approving employee requests
- AI agents acting for users

## Decision
Delegated identity will be implemented using OAuth 2.0 Token Exchange (RFC 8693).

Tokens will include:

- Subject identity
- Actor identity
- Delegation scope
- Delegation expiration

## Consequences

### Positive
- Industry standard approach
- Strong audit trail
- Clear separation between actor and subject

### Negative
- Increased token lifecycle complexity
- Requires downstream service enforcement

## Alternatives Considered
- Session-based impersonation
- Static role delegation

Token exchange selected for standards compliance and audit clarity.
