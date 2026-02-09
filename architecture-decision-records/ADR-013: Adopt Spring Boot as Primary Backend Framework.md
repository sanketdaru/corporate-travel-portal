# ADR-013: Adopt Spring Boot as Primary Backend Framework

## Status
Accepted

## Context
The platform requires:

- Mature OAuth2/OIDC integration
- Microservice support
- Strong ecosystem and tooling
- Alignment with enterprise Java skill sets
- Compatibility with Keycloak adapters and libraries

## Decision
All backend domain services will be implemented using Spring Boot.

Supporting libraries will include:

- Spring Security
- Spring Authorization Client
- Spring Cloud components (selectively)

## Consequences

### Positive
- First-class OAuth2/OIDC support
- Strong integration with Keycloak
- Large enterprise adoption
- Mature observability and resilience ecosystem

### Negative
- JVM startup overhead
- Requires Java expertise

## Alternatives Considered
- Node.js with NestJS
- Quarkus
- Micronaut
- .NET Core

Spring Boot chosen for ecosystem maturity and identity integration depth.
