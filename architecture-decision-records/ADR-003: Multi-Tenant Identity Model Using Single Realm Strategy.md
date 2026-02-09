# ADR-003: Multi-Tenant Identity Model Using Single Realm Strategy

## Status
Accepted

## Context
The application is designed as a SaaS platform serving multiple enterprises.

Two primary approaches exist:

1. Realm per tenant
2. Single realm with tenant isolation

## Decision
A single Keycloak realm will host multiple tenants.

Tenant isolation will be implemented using:

- Organizations mapped to Keycloak groups
- Tenant-specific claims
- Attribute-based access policies

## Consequences

### Positive
- Simplifies operational management
- Enables cross-tenant federation demonstrations
- Allows shared delegation and brokering features

### Negative
- Requires strict authorization enforcement
- Increased policy complexity

## Alternatives Considered
- Realm-per-tenant model
- Hybrid realm model

Single realm chosen to better demonstrate enterprise SaaS IAM complexity.
