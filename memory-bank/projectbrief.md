# Project Brief: Corporate Travel & Expense Platform

## Project Name
Corporate Travel & Expense Platform - Identity Reference Implementation

## Primary Purpose
Build a comprehensive multi-tenant SaaS platform demonstrating advanced identity and access management patterns in a real-world enterprise scenario.

## Core Requirements

### Identity Management Patterns (Primary Focus)
1. **Federated Identity** - Keycloak as central IAM with OIDC/OAuth 2.0
2. **Delegated Identity** - OAuth 2.0 Token Exchange (RFC 8693) for acting on behalf of others
3. **Multi-Tenant Isolation** - Single Keycloak realm with group-based tenant separation
4. **Fine-Grained Authorization** - Open Policy Agent (OPA) for attribute-based access control
5. **Consent Management** - Purpose-bound delegation with explicit consent tracking
6. **Audit Compliance** - Complete actor/subject tracking for all identity-sensitive operations

### Domain Features (Secondary Focus)
1. **Travel Booking** - Flight, hotel, and car rental booking
2. **Expense Management** - Expense submission and tracking
3. **Approval Workflows** - Multi-level approval state machine
4. **Delegation Management** - Graph-based relationship tracking

## Target Architecture
- **Pattern**: Microservices with Backend-for-Frontend (BFF)
- **Backend**: Spring Boot 3.x services with Spring Security OAuth2
- **Frontend**: Next.js 14 + React 18 with shadcn/ui
- **Deployment**: Docker Compose (MVP), Kubernetes-ready structure

## Success Criteria
1. ✅ Demonstrate all 22 Architecture Decision Records (ADRs)
2. ✅ Complete end-to-end delegation flow (assistant booking for executive)
3. ✅ Strict multi-tenant isolation enforced by OPA
4. ✅ Token exchange working with proper actor/subject claims
5. ✅ Comprehensive audit trail for compliance

## Scope Boundaries

### In Scope
- Core identity patterns (authentication, authorization, delegation, consent)
- MVP services (Travel, Expense, Approval, Delegation, Consent)
- Infrastructure setup (Keycloak, PostgreSQL, Neo4j, OPA)
- Documentation and developer experience

### Out of Scope (MVP)
- HashiCorp Vault integration (deferred to post-MVP)
- Keycloak SPI for token enrichment (post-MVP)
- OpenTelemetry observability (post-MVP)
- Production deployment to Kubernetes (structure prepared, deployment post-MVP)
- AI agent integration (future enhancement)

## Key Constraints
- **Development**: Local Docker Compose environment
- **Repository**: Monorepo with Gradle multi-project build
- **Database**: PostgreSQL for relational data, Neo4j for delegation graph
- **Authorization**: External OPA (not embedded Keycloak Authorization Services)
- **UI**: Functional and appealing with standard design system (shadcn/ui)

## Stakeholders
- **Primary**: Developers learning enterprise identity patterns
- **Secondary**: Architects evaluating OAuth 2.0 Token Exchange
- **Tertiary**: Compliance teams understanding audit capabilities
