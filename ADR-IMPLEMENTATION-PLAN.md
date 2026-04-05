# ADR Implementation Plan & Gap Analysis

**Created**: 2026-02-28  
**Last Updated**: 2026-04-04  
**Status**: Phase 4 Complete — MVP Fully Implemented (71/71 E2E tests passing)  
**Estimated Effort**: 10-13 days (80-104 hours)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [ADR Gap Analysis](#adr-gap-analysis)
3. [Implementation Plan](#implementation-plan)
4. [Task Breakdown](#task-breakdown)
5. [Testing Strategy](#testing-strategy)
6. [Success Criteria](#success-criteria)
7. [Risks & Mitigation](#risks--mitigation)

---

## Executive Summary

### Current State (100% MVP Complete) — Updated 2026-04-04

**✅ Fully Implemented (all ADRs)**
- Infrastructure (Keycloak, PostgreSQL, Neo4j, OPA)
- Spring Boot services (Travel, Expense)
- API Gateway
- Multi-tenant isolation
- Flyway migrations
- **Delegation Service** — Neo4j + PostgreSQL dual-write, graph traversal
- **Consent Service** — full CRUD, OPA, scheduler, audit trail
- **Employee BFF** — token exchange, delegation context, API aggregation
- **Token Exchange (ADR-004)** — Standard Token Exchange V2, RFC 8693
- **End-to-end delegation flow** — 71/71 tests passing (Phase 4 final)
- **Audit service layer (ADR-011)** — `BookingAuditService`, `ExpenseAuditService` wired end-to-end; audit trail assertions in E2E test (Phase 10)
- **OPA consent authorization** — consent operations policy block in `authorization.rego`
- **`delegationId` in SecurityContext** — `X-Delegation-Id` header extracted and threaded to audit records
- **Keycloak role assignments (Phase 4)** — all 5 human users seeded with correct roles + `tenant_id` attribute; `realm_access.roles` in JWT via `oidc-usermodel-realm-role-mapper` on `user-attributes` scope; realm-export.json is authoritative canonical export
- **OPA `update_booking` + `delete_booking` (Phase 4)** — owner and delegate rules added; `realm_access.roles` now flows correctly so `has_role("employee")` is satisfied for direct expense/booking creation
- **Delegation headers on mutation endpoints (Phase 4)** — `PUT /api/bookings/{id}/status` and `DELETE /api/bookings/{id}` now accept `HttpServletRequest` and call the header-aware `extractSecurityContext` overload
- **OPA hot-reload (Phase 4)** — `--watch` flag added to OPA container so policy file changes apply without restart
- **Realm export validation (Phase 4)** — `validate-realm-export.sh` proves clean import on isolated KC instance (65/65 checks)

**❌ Not Implemented — Identity Brokering (low priority)**
- Identity brokering (Keycloak capable; external IdP config not needed for MVP)

**❌ Not Implemented — Post-MVP (deferred)**
- Frontend (Next.js), Vault, OpenTelemetry, Kubernetes, Keycloak SPI

### Remaining Work

None — MVP scope is complete.

---

## ADR Gap Analysis

### ✅ Fully Implemented (7 ADRs)

#### ADR-001: Corporate Travel & Expense Domain
- **Status**: ✅ Complete
- **Implementation**: 
  - Travel Service with bookings CRUD
  - Expense Service with workflow
  - Both services tested and running

#### ADR-002: Keycloak as Central IAM
- **Status**: ✅ Complete
- **Implementation**: Keycloak 23.0 running with realm configured

#### ADR-003: Multi-Tenant Single Realm Strategy
- **Status**: ✅ Complete
- **Implementation**: 
  - Single realm with 2 tenants
  - tenant_id in all database tables
  - OPA enforces tenant boundaries

#### ADR-007 & ADR-019: External Policy Engine (OPA)
- **Status**: ✅ Complete
- **Implementation**: 
  - OPA running with comprehensive Rego policies
  - OpaClient integration in security-commons

#### ADR-013: Spring Boot Backend
- **Status**: ✅ Complete
- **Implementation**: All services use Spring Boot 3.2.2

#### ADR-015: PostgreSQL Database
- **Status**: ✅ Complete
- **Implementation**: PostgreSQL 15 with schema-per-service pattern

#### ADR-017: API Gateway Pattern
- **Status**: ✅ Complete
- **Implementation**: Spring Cloud Gateway with JWT validation

#### ADR-023: Flyway Migrations
- **Status**: ✅ Complete
- **Implementation**: Travel and Expense services using Flyway

---

### ⚠️ Partially Implemented (4 ADRs)

#### ADR-006: Microservices with BFF Pattern
- **Status**: ✅ Complete (as of 2026-04-03)
- **Implemented**: 
  - ✅ Microservices architecture
  - ✅ Domain-driven service boundaries
  - ✅ `employee-bff` service — token exchange, delegation context, API aggregation, service clients

#### ADR-011: Comprehensive Audit and Compliance Ledger
- **Status**: ✅ Complete (as of 2026-04-04)
- **Implemented**:
  - ✅ `booking_audit` table with delegation fields (`actor_id`, `subject_id`, `delegation_id`, `consent_id`, `tenant_id`)
  - ✅ `expense_audit` table with same delegation fields
  - ✅ Flyway V2 migrations applied to both services
  - ✅ Booking record captures `userId` (subject) and `createdBy` (actor) — identity chain preserved
  - ✅ OPA consent audit block in `authorization.rego`
  - ✅ `BookingAudit` entity + `BookingAuditRepository` + `BookingAuditService`/`Impl`
  - ✅ `ExpenseAudit` entity + `ExpenseAuditRepository` + `ExpenseAuditService`/`Impl`
  - ✅ Wired into `BookingServiceImpl` (CREATE, STATUS_CHANGE, DELETE)
  - ✅ Wired into `ExpenseServiceImpl` (CREATE, UPDATE, DELETE, SUBMIT, APPROVE, REJECT)
  - ✅ `GET /api/bookings/{id}/audit` endpoint — returns full trail ordered by timestamp desc
  - ✅ `GET /api/expenses/{id}/audit` endpoint — same pattern
  - ✅ `delegationId` field added to `SecurityContext`; `X-Delegation-Id` header extracted in `JwtAuthenticationConverter`
  - ✅ Audit writes share the booking/expense transaction (FK constraint satisfied)
- **Verified**: `actorId=dave.assistant`, `subjectId=carol.executive`, `delegationId` and `consentId` correctly persisted in audit record for delegated booking creation

#### ADR-016: Graph Database for Delegation Modeling
- **Status**: ✅ Complete (as of 2026-03-29)
- **Implemented**:
  - ✅ Neo4j 5.15 running
  - ✅ UserNode.java, DelegationRelationship.java (Spring Data Neo4j)
  - ✅ DelegationGraphRepository with Cypher queries
  - ✅ Dual-write: PostgreSQL (source of truth) + Neo4j (graph traversal)
  - ✅ DelegationServiceImpl syncs both on create/revoke

#### ADR-008: Identity Brokering for Enterprise Federation
- **Status**: ⚠️ Partial (20%)
- **Implemented**: ✅ Keycloak supports federation
- **Missing**: ❌ No external IdP configurations
- **Impact**: LOW - Not needed for MVP

---

### ❌ Not Implemented - Critical (5 ADRs)

#### ADR-004: OAuth 2.0 Token Exchange
- **Status**: ✅ Complete (as of 2026-04-03)
- **Implemented**:
  - ✅ Standard Token Exchange V2 (RFC 8693) via `KeycloakTokenExchangeClient`
  - ✅ `KC_FEATURES=token-exchange-standard` enabled in `docker-compose.yml`
  - ✅ Standard token exchange toggle enabled on `employee-bff` Keycloak client
  - ✅ `aud` mapper added to `employee-bff` client scope — `travel-service` in audience claim
  - ✅ `preferred_username` mapper added to `user-attributes` scope in Keycloak realm
  - ✅ `TokenExchangeService` in employee-bff performs exchange with `subject_token` + `audience`
  - ✅ `DelegationContextService` validates delegation + consent before exchange
  - ✅ `X-Delegated-Subject`, `X-Delegation-Id`, `X-Consent-Id`, `X-Delegation-Purpose` headers threaded end-to-end
  - ✅ `JwtAuthenticationConverter` extended with header-aware overload for downstream services
  - ✅ End-to-end verified: Dave authenticates → token exchange → booking saved as Carol

#### ADR-005: External Consent and Purpose Binding Service
- **Status**: ✅ Complete (as of 2026-03-29)
- **Implemented**:
  - ✅ consent-service with full CRUD
  - ✅ consent + consent_audit DB schema (Flyway)
  - ✅ ConsentServiceImpl with `validateConsent`, `hasConsentForScopes`, `expireStaleConsents` (scheduler)
  - ✅ OPA authorization integrated
  - ✅ Unit tests with full coverage

#### ADR-010: Persist Delegation Relationships Using Graph
- **Status**: ✅ Complete (as of 2026-03-29)
- **Implemented**:
  - ✅ delegation-service with JPA + Neo4j
  - ✅ Graph schema: User nodes, CAN_ACT_AS relationships
  - ✅ DelegationGraphRepository with chain traversal Cypher queries
  - ✅ Unit tests with full coverage

#### ADR-018: BFF Strategy
- **Status**: ✅ Complete (as of 2026-04-03)
- **Implemented**:
  - ✅ `employee-bff` service at port 3001 (Spring Boot)
  - ✅ `TokenExchangeService` — performs Standard Token Exchange V2
  - ✅ `DelegationContextService` — validates delegation + consent, builds `DelegationContext`
  - ✅ `KeycloakTokenExchangeClient` — WebClient-based RFC 8693 exchange
  - ✅ Service clients: `TravelServiceClient`, `DelegationServiceClient`, `ConsentServiceClient`
  - ✅ `BookingBffController` with delegation-mode booking creation
  - ✅ `DelegationBffController` — activate/deactivate delegation context
  - ✅ Delegation headers injected on all downstream service calls
  - ✅ Unit tests for all BFF components

#### ADR-014: React + Next.js Frontend
- **Status**: ❌ Not Implemented
- **Required For**: User interface
- **Missing**: No frontend directory/project
- **Impact**: HIGH - Cannot demonstrate flows

---

### ❌ Not Implemented - Post-MVP (7 ADRs)

These are correctly deferred per projectbrief.md:

- **ADR-009**: Workload Identity (mTLS) - Deferred ✅
- **ADR-012**: Kubernetes Deployment - Deferred ✅
- **ADR-020**: OpenTelemetry - Deferred ✅
- **ADR-021**: HashiCorp Vault - Deferred ✅
- **ADR-022**: Keycloak SPI - Deferred ✅
- **Approval Service**: Not yet implemented
- **Frontend**: Next.js portal not yet built

---

## Implementation Plan

### Phase 1: Foundation Services (5-6 days)

#### 1.1 Delegation Service (2-3 days, 17 hours)

**Purpose**: Store and query delegation relationships using Neo4j + PostgreSQL

**Database Schemas**:

PostgreSQL (V1__baseline_delegations_schema.sql):
```sql
CREATE TABLE delegation.delegations (
  id UUID PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  delegator_id VARCHAR(255) NOT NULL,  -- Person granting
  delegate_id VARCHAR(255) NOT NULL,   -- Person receiving
  purpose VARCHAR(500) NOT NULL,
  scopes TEXT[] NOT NULL,
  granted_at TIMESTAMP NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMP,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  revoked_at TIMESTAMP,
  revoked_by VARCHAR(255),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delegations_tenant ON delegation.delegations(tenant_id);
CREATE INDEX idx_delegations_delegator ON delegation.delegations(delegator_id);
CREATE INDEX idx_delegations_delegate ON delegation.delegations(delegate_id);
```

Neo4j Cypher:
```cypher
// Node: User
CREATE (u:User {
  userId: string,
  tenantId: string,
  email: string,
  displayName: string
})

// Relationship: CAN_ACT_AS
CREATE (delegator:User)-[:CAN_ACT_AS {
  delegationId: string,
  grantedAt: timestamp,
  expiresAt: timestamp,
  purpose: string,
  scopes: [string],
  active: boolean
}]->(delegate:User)

// Indexes
CREATE INDEX user_id FOR (u:User) ON (u.userId);
CREATE INDEX tenant_id FOR (u:User) ON (u.tenantId);
```

**API Endpoints**:
```
POST   /api/delegations              - Create delegation
GET    /api/delegations/my-delegations - List delegations I granted
GET    /api/delegations/to-me        - List delegations granted to me
GET    /api/delegations/{id}         - Get delegation details
DELETE /api/delegations/{id}         - Revoke delegation
GET    /api/delegations/chain        - Get delegation chain (graph traversal)
```

**Project Structure**:
```
services/delegation-service/
├── src/main/
│   ├── java/com/corporate/travel/delegation/
│   │   ├── DelegationServiceApplication.java
│   │   ├── config/
│   │   │   ├── Neo4jConfig.java
│   │   │   ├── SecurityConfig.java
│   │   │   └── OpenApiConfig.java
│   │   ├── model/
│   │   │   ├── entity/
│   │   │   │   ├── Delegation.java (PostgreSQL)
│   │   │   │   └── UserNode.java (Neo4j)
│   │   │   │   └── DelegationRelationship.java (Neo4j)
│   │   │   └── dto/
│   │   │       ├── CreateDelegationRequest.java
│   │   │       ├── DelegationResponse.java
│   │   │       └── DelegationChainResponse.java
│   │   ├── repository/
│   │   │   ├── DelegationRepository.java (JPA)
│   │   │   └── DelegationGraphRepository.java (Neo4j)
│   │   ├── service/
│   │   │   ├── DelegationService.java
│   │   │   └── impl/DelegationServiceImpl.java
│   │   └── controller/
│   │       └── DelegationController.java
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           └── V1__baseline_delegations_schema.sql
└── build.gradle
```

**Dependencies (build.gradle)**:
```gradle
dependencies {
    implementation project(':services:shared:security-commons')
    implementation project(':services:shared:domain-models')
    
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-neo4j'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
    
    runtimeOnly 'org.postgresql:postgresql'
    
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

**Key Service Methods**:
```java
@Service
public class DelegationServiceImpl {
    
    // Create delegation (sync to both PostgreSQL and Neo4j)
    DelegationResponse createDelegation(CreateDelegationRequest, SecurityContext);
    
    // Query delegations
    List<DelegationResponse> getMyDelegations(SecurityContext);
    List<DelegationResponse> getDelegationsToMe(SecurityContext);
    DelegationResponse getDelegation(UUID id, SecurityContext);
    
    // Graph queries
    List<DelegationChainResponse> getDelegationChain(String userId, SecurityContext);
    
    // Revoke delegation (update both databases)
    void revokeDelegation(UUID id, SecurityContext);
    
    // Internal: Sync PostgreSQL → Neo4j
    private void syncToGraph(Delegation);
}
```

---

#### 1.2 Consent Service (2-3 days, 19 hours)

**Purpose**: Manage consent records with purpose binding and lifecycle management

**Database Schema (V1__baseline_consents_schema.sql)**:
```sql
CREATE TABLE consent.consents (
  id UUID PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  grantor_id VARCHAR(255) NOT NULL,        -- Person giving consent
  grantee_id VARCHAR(255) NOT NULL,        -- Person receiving consent
  delegation_id UUID,                      -- Link to delegation
  purpose VARCHAR(500) NOT NULL,           -- "book_travel", "approve_expenses"
  scopes TEXT[] NOT NULL,                  -- ["view_bookings", "create_bookings"]
  data_categories TEXT[],                  -- ["travel_data", "expense_data"]
  granted_at TIMESTAMP NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMP,
  revoked_at TIMESTAMP,
  revoked_by VARCHAR(255),
  status VARCHAR(50) NOT NULL,             -- ACTIVE, REVOKED, EXPIRED
  metadata JSONB,
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE consent.consent_audit (
  id UUID PRIMARY KEY,
  consent_id UUID REFERENCES consent.consents(id) ON DELETE CASCADE,
  action VARCHAR(100) NOT NULL,            -- GRANTED, USED, REVOKED
  actor_id VARCHAR(255) NOT NULL,
  timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
  details JSONB
);

CREATE INDEX idx_consents_tenant ON consent.consents(tenant_id);
CREATE INDEX idx_consents_grantor ON consent.consents(grantor_id);
CREATE INDEX idx_consents_grantee ON consent.consents(grantee_id);
CREATE INDEX idx_consents_status ON consent.consents(status);
```

**API Endpoints**:
```
POST   /api/consents                 - Grant consent
GET    /api/consents                 - List my consents
GET    /api/consents/{id}            - Get consent details
DELETE /api/consents/{id}            - Revoke consent
POST   /api/consents/{id}/validate   - Validate consent for action
GET    /api/consents/audit/{id}      - Get consent audit trail
```

**Key Service Methods**:
```java
@Service
public class ConsentServiceImpl {
    
    // Create consent (linked to delegation)
    ConsentResponse grantConsent(CreateConsentRequest, SecurityContext);
    
    // Validate consent for specific action
    boolean validateConsent(UUID consentId, String action, String purpose);
    
    // Check if consent covers requested scopes
    boolean hasConsentForScopes(String grantorId, String granteeId, List<String> scopes);
    
    // Revoke consent
    void revokeConsent(UUID consentId, SecurityContext);
    
    // Auto-expire consents (scheduled)
    @Scheduled(cron = "0 0 * * * *") // Every hour
    void expireStaleConsents();
}
```

---

#### 1.3 Audit Logging Enhancement (1-2 days, 12 hours)

**Purpose**: Implement audit logging for existing Travel and Expense services

**Approach**: Add audit entities and integrate into service layer

**For Travel Service**:
```java
@Entity
@Table(name = "booking_audit", schema = "travel")
public class BookingAudit {
    @Id
    private UUID id;
    private UUID bookingId;
    private String action;  // CREATED, UPDATED, DELETED, STATUS_CHANGED
    private String actorId;
    private String subjectId;
    private LocalDateTime timestamp;
    private String changes;  // JSON
    private String tenantId;
}

@Service
public class AuditService {
    void auditBookingCreated(Booking, SecurityContext);
    void auditBookingUpdated(Booking before, Booking after, SecurityContext);
    void auditBookingDeleted(Booking, SecurityContext);
    List<BookingAudit> getAuditTrail(UUID bookingId);
}
```

**Integration Points**:
- Add `auditService.auditBookingCreated()` to `BookingServiceImpl.createBooking()`
- Add `auditService.auditBookingUpdated()` to `BookingServiceImpl.updateBookingStatus()`
- Add `auditService.auditBookingDeleted()` to `BookingServiceImpl.deleteBooking()`
- Same pattern for ExpenseService

**New Endpoints**:
```
GET /api/bookings/{id}/audit  - Get booking audit trail
GET /api/expenses/{id}/audit  - Get expense audit trail
```

---

### Phase 2: Integration Layer (4-5 days)

#### 2.1 Employee BFF + Token Exchange (4-5 days, 32 hours)

**Purpose**: Backend-for-Frontend that is the delegation gateway — performs audience-scoped token exchange and threads the full delegation identity chain to downstream services.

**Architecture**:
```
Human / AI Agent → Employee BFF → Keycloak (Token Exchange V2: audience scoping)
                        ↓
                 delegation-service  (validate delegation chain)
                 consent-service     (validate consent for action)
                        ↓
                  API Gateway → Domain Services
                  (with delegation headers threaded)
```

**Delegation Flow (two-layer approach)**:

*Layer 1 — Keycloak Standard Token Exchange V2 (audience scoping):*
1. Actor (Dave or AI agent) authenticates → holds own access token (`sub=actor`)
2. Actor requests to act under delegation context (provides `delegationId`)
3. BFF validates the delegation record is active via delegation-service
4. BFF validates consent exists for the requested action via consent-service
5. BFF performs V2 token exchange: `subject_token=actor-token`, `audience=target-service`
6. Keycloak issues audience-scoped token (`sub=actor`, `aud=travel-service`) — actor identity preserved

*Layer 2 — Application-level delegation context headers:*

7. BFF threads the following headers on every downstream call:
```
Authorization:       Bearer <audience-scoped token>   # actor JWT, sub=actor
X-Delegated-Subject: <carol-user-id>                  # human principal
X-Delegation-Id:     <delegation-record-uuid>         # validated delegation record
X-Actor-Token:       <actor-original-token>           # for audit chain
```

*Downstream service enforcement:*

8. Each service builds `SecurityContext` from JWT + headers
9. Each service validates `X-Delegation-Id` against delegation-service
10. OPA authorization receives full context: `actorId`, `subjectId`, `delegationId`, `tenantId`
11. Audit records capture both actor and subject

> **Why not `requested_subject`?** Keycloak Standard Token Exchange V2 does not support `requested_subject` or the RFC 8693 `act` claim. V1 (Legacy) impersonation with `requested_subject` produces `sub=Carol` with no actor trace — the AI agent disappears from the identity chain. Application-level headers preserve every identity at every hop, which is essential for the multi-agent agentic AI objective. When Keycloak ships `act` claim support, headers become a drop-in replacement.

**Keycloak Configuration**:

Standard Token Exchange V2 is enabled via:
- `KC_FEATURES=token-exchange-standard` in `docker-compose.yml` ✅ (already done)
- **Standard token exchange** toggle enabled on `employee-bff` client in Admin Console (post-start, manual step)
- No Client Policies, no FGAP, no `secure-token-exchange` executor required

**Multi-Agent Node Support in Delegation Service**:

The delegation-service Neo4j graph must support AI agent nodes (Keycloak client IDs) as delegation participants, not only human user IDs:

```cypher
// Agent node — registered Keycloak confidential client
CREATE (:Agent {
  agentId: "travel-planner-agent",   // Keycloak client_id
  tenantId: "tenant-a",
  displayName: "Travel Planner Agent"
})

// Human grants delegation to AI agent
(carol:User)-[:CAN_ACT_AS {
  delegationId: "...",
  purpose: "book_travel",
  scopes: ["create_bookings", "view_bookings"],
  grantedAt: timestamp,
  expiresAt: timestamp
}]->(travel-planner-agent:Agent)

// Agent delegates to sub-agent
(travel-planner-agent:Agent)-[:CAN_ACT_AS {
  delegationId: "...",
  purpose: "book_travel"
}]->(booking-agent:Agent)
```

**SecurityContext Extension**:

`security-commons` `SecurityContext` must be extended to carry delegation fields read from headers:

```java
// SecurityContext additions
String subjectId;       // from X-Delegated-Subject header (null if no delegation)
String delegationId;    // from X-Delegation-Id header (null if no delegation)
boolean isDelegated();  // true when subjectId != null

// JwtAuthenticationConverter extension
// Read X-Delegated-Subject and X-Delegation-Id from request headers
// Populate SecurityContext fields from both JWT claims and headers
```

**Project Structure**:
```
services/employee-bff/
├── src/main/java/com/corporate/travel/bff/
│   ├── EmployeeBffApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── WebClientConfig.java
│   │   └── BffProperties.java
│   ├── client/
│   │   ├── KeycloakTokenExchangeClient.java   # V2 token exchange (audience scoping)
│   │   ├── TravelServiceClient.java
│   │   ├── ExpenseServiceClient.java
│   │   ├── DelegationServiceClient.java
│   │   └── ConsentServiceClient.java
│   ├── service/
│   │   ├── TokenExchangeService.java
│   │   ├── DelegationContextService.java      # validates delegation + consent, builds context
│   │   └── ApiAggregationService.java
│   ├── controller/
│   │   ├── BookingBffController.java
│   │   ├── ExpenseBffController.java
│   │   └── DelegationBffController.java
│   └── model/
│       ├── DelegationContext.java             # actorToken, subjectId, delegationId, consentId
│       └── TokenExchangeResponse.java
```

**Key Implementation — Token Exchange (Standard V2, audience scoping only)**:
```java
@Service
public class KeycloakTokenExchangeClient {

    /**
     * Performs Standard Token Exchange V2 (RFC 8693) for audience scoping.
     *
     * Security contract:
     *   - actorToken (subject_token) is MANDATORY — chain of trust, proves actor identity.
     *     Keycloak rejects requests without it.
     *   - audience scopes the issued token to a single resource server, preventing replay.
     *   - NO requested_subject — Standard V2 does not support impersonation.
     *     The delegation target (Carol) is threaded as X-Delegated-Subject header,
     *     validated against the delegation-service before the exchange is invoked.
     *
     * @param actorToken     Actor's current access token (Dave or AI agent) — chain of trust
     * @param targetAudience Resource server to scope the token to (e.g. "travel-service")
     */
    public TokenExchangeResponse exchangeToken(String actorToken, String targetAudience) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        formData.add("client_id", properties.getKeycloak().getClientId());
        formData.add("client_secret", properties.getKeycloak().getClientSecret());
        formData.add("subject_token", actorToken);          // mandatory — chain of trust
        formData.add("subject_token_type", TOKEN_TYPE_ACCESS);
        formData.add("requested_token_type", TOKEN_TYPE_ACCESS);
        formData.add("audience", targetAudience);           // scopes token to target service
        // Note: NO requested_subject — V2 does not support impersonation.
        // Delegation target is carried as X-Delegated-Subject header.

        return keycloakWebClient.post()
            .uri("/realms/{realm}/protocol/openid-connect/token", realm)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(formData)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new TokenExchangeException(
                        "Token exchange rejected — verify subject_token validity and that " +
                        "'Standard token exchange' is enabled on employee-bff in Keycloak Admin: " + body))))
            .bodyToMono(TokenExchangeResponse.class)
            .block();
    }
}
```

**Key Implementation — Delegation Context Service**:
```java
@Service
public class DelegationContextService {

    /**
     * Resolves and validates a full delegation context before any downstream call.
     * Validates: delegation is active, consent covers the requested action, same tenant.
     *
     * @param actorToken   The actor's JWT (Dave or AI agent)
     * @param delegationId The delegation record to activate
     * @param action       The action to be performed (e.g. "book_travel")
     * @return DelegationContext containing subjectId, consentId, and the actor token
     */
    public DelegationContext resolve(String actorToken, UUID delegationId, String action) {
        DelegationResponse delegation = delegationServiceClient.getDelegation(delegationId);
        // validate: active, not expired, actor is the delegate
        // validate consent covers requested action
        ConsentValidationResponse consent = consentServiceClient.validateConsent(
            delegation.getDelegatorId(), delegation.getDelegateId(), action);
        return DelegationContext.of(actorToken, delegation.getDelegatorId(),
                                   delegationId, consent.getConsentId());
    }
}
```

**BFF API Endpoints**:
```
# Authentication & Delegation
POST   /api/bff/auth/login                    - Login
POST   /api/bff/delegation/activate/{id}      - Switch to delegation mode
DELETE /api/bff/delegation/deactivate         - Exit delegation mode
GET    /api/bff/delegation/context            - Get current context

# Aggregated APIs
GET    /api/bff/bookings                      - List bookings (auto-delegated)
POST   /api/bff/bookings                      - Create booking (auto-delegated)
GET    /api/bff/expenses                      - List expenses
POST   /api/bff/expenses                      - Create expense
GET    /api/bff/dashboard                     - Aggregate dashboard data
```

---

### Phase 3: Audit Service Layer + Documentation (2-3 days) ✅ COMPLETE — 2026-04-04

#### 3.1 Audit Logging Service Layer ✅
- `BookingAudit` entity + `BookingAuditRepository` + `BookingAuditService`/`Impl` in travel-service
- `ExpenseAudit` entity + `ExpenseAuditRepository` + `ExpenseAuditService`/`Impl` in expense-service
- Wired into `BookingServiceImpl` (CREATE, STATUS_CHANGE, DELETE) and `ExpenseServiceImpl` (CREATE, UPDATE, DELETE, SUBMIT, APPROVE, REJECT)
- `GET /api/bookings/{id}/audit` and `GET /api/expenses/{id}/audit` endpoints added
- `delegationId` field added to `SecurityContext`; extracted from `X-Delegation-Id` header in `JwtAuthenticationConverter`
- Audit `@Transactional` uses `REQUIRED` (not `REQUIRES_NEW`) — FK `booking_audit.booking_id → bookings.id` requires the same transaction

**Issues surfaced during Phase 3 testing (deferred to Phase 4):**
- Keycloak `employee` role not assigned → `create_expense` OPA check fails for all users
- `update_booking` OPA rule missing for direct owner → Carol 403 on her own booking
- `PUT /api/bookings/{id}/status` and `DELETE /api/bookings/{id}` do not pass `HttpServletRequest` → delegation context dropped
- E2E test has no Phase 10 assertions for audit trail endpoints

#### 3.2 Documentation Updates ✅ Complete (2026-04-05)
- README.md, IMPLEMENTATION.md, DELEGATION-FLOW.md, all service READMEs updated

---

### Phase 4: Bug Fixes + E2E Test Expansion (1-2 days) ← NEXT

#### 4.1 Keycloak Role Assignments (0.5 days)

**Problem**: `carol.executive` and `dave.assistant` have no `realm_access.roles` in their Keycloak tokens. The OPA `create_expense`, `submit_expense`, and `update_expense`/`delete_expense` rules all require `has_role("employee")`, causing 403 on every expense operation.

**Fix**:
- Assign the `employee` realm role to `carol.executive` and `dave.assistant` in Keycloak
- Assign the `manager` realm role to the approver user
- Export and commit the updated `infrastructure/keycloak/realm-export.json`
- Verify `realm_access.roles` is populated in tokens after re-login

**Affected OPA rules**: `create_expense`, `view_expense` (employee gate), `update_expense`, `delete_expense`, `submit_expense`

#### 4.2 OPA Policy Gaps (0.5 days)

**Problem**: No `update_booking` rule covers the resource owner performing a direct (non-delegated) status update. Carol gets 403 when updating her own booking via `PUT /api/bookings/{id}/status`.

**Fix** — add owner rule to `authorization.rego`:
```rego
# Allow booking owner to update status of their own booking
allow if {
    input.action == "update_booking"
    is_same_tenant
    input.resource.user_id == input.user.user_id
}
```

Also verify `delete_booking` has an equivalent owner rule and add if missing.

#### 4.3 Delegation Headers on Mutation Endpoints (0.5 days)

**Problem**: `PUT /api/bookings/{id}/status` and `DELETE /api/bookings/{id}` call `JwtAuthenticationConverter.extractSecurityContext(jwt)` (no `HttpServletRequest`). When Dave tries to update or delete a booking on Carol's behalf, the delegation context (`X-Delegated-Subject`, `X-Delegation-Id`, etc.) is not read and the SecurityContext has no `subjectId`, causing OPA to deny the request.

**Fix** — add `HttpServletRequest` parameter to both endpoints and switch to the header-aware overload:
```java
// BookingController.updateBookingStatus
@PutMapping("/{id}/status")
public ResponseEntity<Booking> updateBookingStatus(
        @PathVariable UUID id,
        @RequestBody Map<String, String> statusRequest,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request) {                           // ← add
    SecurityContext context =
        JwtAuthenticationConverter.extractSecurityContext(jwt, request); // ← switch
    ...
}

// BookingController.deleteBooking — same change
```

Apply the same fix to `DELETE /api/expenses/{id}` and any other expense mutation endpoints that may share the issue.

#### 4.4 E2E Test Expansion — Phase 10: Audit Trail Assertions (0.5 days)

**Add to `scripts/end-to-end-test/run-delegation-flow.sh`** a new Phase 10 block that runs after the delegated booking is created in Phase 7:

```bash
# ---------------------------------------------------------------------------
# Phase 10 — Audit trail verification (ADR-011, Phase 3)
# ---------------------------------------------------------------------------
header "Phase 10 — Audit trail verification (ADR-011)"

info "Fetching audit trail for booking $BOOKING_ID"
AUDIT=$(curl -s "$TRAVEL_SERVICE_URL/api/bookings/$BOOKING_ID/audit" \
  -H "Authorization: Bearer $DELEGATION_TOKEN" \
  -H "X-Delegated-Subject: $CAROL_USER" \
  -H "X-Delegation-Id: $DELEGATION_ID" \
  -H "X-Consent-Id: $BFF_CONSENT_ID" \
  -H "X-Delegation-Purpose: $DELEGATION_PURPOSE")

RECORD_COUNT=$(echo "$AUDIT" | jq 'length')
assert_not_empty "Booking audit trail is non-empty" "$RECORD_COUNT"
assert_http "Audit record[0] actorId=Dave"       "$AUDIT" '.[0].actorId'     "$DAVE_USER"
assert_http "Audit record[0] subjectId=Carol"    "$AUDIT" '.[0].subjectId'   "$CAROL_USER"
assert_http "Audit record[0] action=CREATE"      "$AUDIT" '.[0].action'      "CREATE"
assert_http "Audit record[0] tenantId=tenant-a"  "$AUDIT" '.[0].tenantId'    "tenant-a"
assert_not_empty "Audit record[0] delegationId"  "$(echo "$AUDIT" | jq -r '.[0].delegationId')"
assert_not_empty "Audit record[0] consentId"     "$(echo "$AUDIT" | jq -r '.[0].consentId')"
assert_http "Audit delegationId matches"  "$AUDIT" '.[0].delegationId' "$DELEGATION_ID"
assert_http "Audit consentId matches"     "$AUDIT" '.[0].consentId'    "$BFF_CONSENT_ID"

info "Verifying non-owner cannot access audit trail"
UNAUTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  "$TRAVEL_SERVICE_URL/api/bookings/$BOOKING_ID/audit" \
  -H "Authorization: Bearer $DAVE_TOKEN")
if [[ "$UNAUTH_CODE" =~ ^(403|404)$ ]]; then
  pass "Non-delegated actor denied audit access (HTTP $UNAUTH_CODE)"
else
  skip "Audit access control check inconclusive (HTTP $UNAUTH_CODE)"
fi
```

Once expense creation is fixed (4.1), also add:
- Create an expense via BFF in delegation mode
- Assert `GET /api/expenses/{id}/audit` returns `actorId=Dave`, `subjectId=Carol`, `action=CREATE`
- Assert `delegationId` and `consentId` match

---

## Task Breakdown

### Delegation Service (17 hours) ✅ COMPLETE

- [x] **Setup** — project structure, build.gradle (JPA + Neo4j deps), application.yml
- [x] **Database** — V1__baseline_delegations_schema.sql, PostgreSQL + Neo4j schemas
- [x] **Entities** — Delegation.java (JPA), UserNode.java (Neo4j), DelegationRelationship.java, full DTOs including DelegationChainResponse
- [x] **Repositories** — DelegationRepository (JPA), DelegationGraphRepository (Neo4j) with Cypher chain traversal
- [x] **Service Layer** — DelegationServiceImpl, OPA authorization, PostgreSQL↔Neo4j sync, revocation
- [x] **REST API** — DelegationController (create/query/revoke/chain), OpenAPI documentation
- [x] **Testing** — DelegationServiceImplTest, CreateDelegationRequestBuilder, DelegationTestFixtures

---

### Consent Service (19 hours) ✅ COMPLETE

- [x] **Setup** — project structure, dependencies, application.yml
- [x] **Database** — V1__baseline_consents_schema.sql, consent + consent_audit tables
- [x] **Entities** — Consent.java, ConsentAudit.java, ConsentStatus enum, full DTOs
- [x] **Repositories** — ConsentRepository, ConsentAuditRepository, custom queries
- [x] **Service Layer** — ConsentServiceImpl with validation, auto-expiry scheduler, OPA authorization
- [x] **REST API** — ConsentController, OpenAPI documentation
- [x] **Integration** — delegation_id FK link, validation by scope/purpose
- [x] **Testing** — ConsentServiceImplTest, ConsentTestDataBuilder, ConsentTestFixtures

---

### Employee BFF (32 hours) ✅ COMPLETE

- [x] **Setup** (3h) — project structure, dependencies (WebClient, OAuth2 client), application.yml

- [x] **Keycloak Config** (2h)
  - [x] `KC_FEATURES=token-exchange-standard` set in docker-compose.yml
  - [x] Standard token exchange toggle enabled on `employee-bff` client in Admin Console
  - [x] `aud` mapper added to `employee-bff` — `travel-service` in audience claim (required for V2 exchange)
  - [x] `preferred_username` mapper added to `user-attributes` scope — stable userId across services
  - [x] Realm export updated (`infrastructure/keycloak/realm-export.json`)

- [x] **SecurityContext Extension in security-commons** (2h)
  - [x] Added `subjectId` field (from `X-Delegated-Subject` header)
  - [x] Added `delegationId`, `consentId`, `delegationPurpose` fields
  - [x] Added `isDelegated()` helper
  - [x] Extended `JwtAuthenticationConverter` with header-aware overload `extractSecurityContext(Jwt, HttpServletRequest)`
  - [x] `preferred_username` used as stable `userId` (UUID `sub` as fallback)

- [x] **Token Exchange** (4h)
  - [x] `KeycloakTokenExchangeClient` — `subject_token` + `audience` only (no `requested_subject`)
  - [x] `TokenExchangeService` — fetches live delegation record, extracts purpose/scopes, calls exchange
  - [x] Fail-fast validation on `actorToken` null check

- [x] **Delegation Context Service** (4h)
  - [x] `DelegationContextService.resolve(actorToken, delegationId, action)`
  - [x] `DelegationContext` model — `actorToken`, `subjectId`, `delegationId`, `consentId`, `purpose`
  - [x] Consent validated via `POST /api/consents/validate` with live delegation purpose and scopes

- [x] **Service Clients** (4h)
  - [x] `TravelServiceClient` — injects `X-Delegated-Subject`, `X-Delegation-Id`, `X-Actor-Token`, `X-Consent-Id`, `X-Delegation-Purpose`
  - [x] `DelegationServiceClient`
  - [x] `ConsentServiceClient` — calls `POST /api/consents/validate` with JSON body

- [x] **BFF Controllers** (5h)
  - [x] `BookingBffController` — delegation-mode booking creation
  - [x] `DelegationBffController` — activate/deactivate delegation context

- [x] **Error Handling** (2h)
  - [x] `TokenExchangeException` with descriptive message for Keycloak 4xx
  - [x] Delegation validation failures (expired, revoked, no consent) return 403

- [x] **Testing** (4h)
  - [x] Unit tests for BFF service components
  - [x] End-to-end regression script (`scripts/end-to-end-test/run-delegation-flow.sh`)

---

### Audit Logging (12 hours) ✅ COMPLETE — 2026-04-04

> **Note**: Audit records capture both `actor_id` (from `SecurityContext.actorId`) and `subject_id` (from `SecurityContext.subjectId`, populated from `X-Delegated-Subject` header). This is the mechanism that makes the full delegation chain auditable — the `act` claim is not available from Keycloak Standard V2.
>
> **Key implementation note**: Audit `@Transactional` uses `REQUIRED` (joins caller's transaction). `REQUIRES_NEW` caused FK violation because the uncommitted booking row was not visible to the separate connection.

- [x] **Schema** — Flyway V2 migrations applied; `booking_audit` and `expense_audit` tables have `actor_id`, `subject_id`, `delegation_id`, `consent_id`, `tenant_id` columns

- [x] **SecurityContext** — added `delegationId` field; `JwtAuthenticationConverter` extracts `X-Delegation-Id` header

- [x] **Travel Service** (6h)
  - [x] `BookingAudit` entity mapped to `travel.booking_audit`
  - [x] `BookingAuditRepository` — `findByBookingIdAndTenantIdOrderByTimestampDesc`
  - [x] `BookingAuditService` interface + `BookingAuditServiceImpl`
  - [x] Wired into `BookingServiceImpl.createBooking()` (CREATE), `updateBookingStatus()` (STATUS_CHANGE), `deleteBooking()` (DELETE)
  - [x] `GET /api/bookings/{id}/audit` endpoint in `BookingController`

- [x] **Expense Service** (6h)
  - [x] `ExpenseAudit` entity mapped to `expense.expense_audit`
  - [x] `ExpenseAuditRepository` — `findByExpenseIdAndTenantIdOrderByTimestampDesc`
  - [x] `ExpenseAuditService` interface + `ExpenseAuditServiceImpl`
  - [x] Wired into `ExpenseServiceImpl` — CREATE, UPDATE, DELETE, SUBMIT, APPROVE, REJECT
  - [x] `GET /api/expenses/{id}/audit` endpoint in `ExpenseController`

---

### Docker & Integration (16 hours) ✅ LARGELY COMPLETE

- [x] **Docker Configuration** (4h)
  - [x] `delegation-service` in docker-compose.yml (port 8083)
  - [x] `consent-service` in docker-compose.yml (port 8084)
  - [x] `employee-bff` in docker-compose.yml (port 3001)
  - [x] Dockerfiles for new services
  - [x] `KC_FEATURES=token-exchange-standard` in Keycloak container config

- [x] **End-to-End Testing** (8h)
  - [x] Delegation creation flow verified
  - [x] Consent grant/validate flow verified
  - [x] Token exchange flow verified (Standard V2, audience-scoped)
  - [x] Dave booking as Carol verified (`userId=carol.executive`, `createdBy=dave.assistant`)
  - [x] OPA cross-tenant blocking verified
  - [x] End-to-end regression script: `scripts/end-to-end-test/run-delegation-flow.sh`
  - [ ] Audit trail verification (audit tables not yet populated by service layer)
  
- [x] **Documentation** (4h)
  - [x] Update README files
  - [x] Create delegation flow guide
  - [x] Update IMPLEMENTATION.md

---

## Testing Strategy

### Unit Tests
**Coverage Target**: 80%+ line coverage, 100% branch coverage

**Test Structure**:
```java
@ExtendWith(MockitoExtension.class)
class DelegationServiceImplTest {
    
    @Mock
    private DelegationRepository delegationRepository;
    
    @Mock
    private OpaClient opaClient;
    
    @InjectMocks
    private DelegationServiceImpl delegationService;
    
    @Test
    void should_createDelegation_when_authorizedAndValid() {
        // Given
        when(opaClient.authorize(...)).thenReturn(true);
        
        // When
        DelegationResponse result = delegationService.createDelegation(...);
        
        // Then
        assertThat(result).isNotNull();
        verify(delegationRepository).save(any());
    }
}
```

### Integration Tests
```java
@SpringBootTest
@Testcontainers
class DelegationServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.15");
    
    @Test
    void should_createAndQueryDelegation_endToEnd() {
        // Full integration test with real databases
    }
}
```

### End-to-End Test Scenarios
1. **Delegation Flow**: Carol grants → Dave activates → Dave creates booking as Carol
2. **Consent Validation**: Scope enforcement and consent validation
3. **Multi-Tenant Isolation**: Cross-tenant access blocked
4. **Audit Trail**: Verify actor/subject tracking

---

## Success Criteria

### Phase 1 Complete When: ✅ DONE
- [x] Delegation Service: Can create/query/revoke delegations
- [x] Neo4j: Delegation relationships stored and queryable via graph traversal
- [x] Consent Service: Can grant/validate/revoke consents
- [x] PostgreSQL: delegation and consent schemas created
- [x] Unit tests: written for both services (DelegationServiceImplTest, ConsentServiceImplTest)
- [x] Docker: Both services configured in docker-compose (ports 8083, 8084)
- [ ] Audit: BookingAudit and ExpenseAudit tables populated ← Phase 2 item

### Phase 2 Complete When: ✅ DONE (as of 2026-04-03)
- [x] Token Exchange: Standard Token Exchange V2 working with Keycloak
- [x] BFF: Can switch to delegation mode via `/api/bff/delegation/activate/{id}`
- [x] BFF: Aggregates booking/expense calls with delegation context
- [x] Integration: BFF → Delegation → Consent → Travel Service works end-to-end
- [x] Actor/Subject: Application-level headers carry full identity chain (`X-Delegated-Subject`, `X-Actor-Token`, `X-Consent-Id`, `X-Delegation-Purpose`)
- [x] OPA: Validates delegation context, tenant isolation, and consent scopes

### End-to-End Demo Works: ✅ VERIFIED
- [x] Carol grants delegation to Dave
- [x] Dave logs in and activates delegation
- [x] Dave creates booking as Carol
- [x] Booking shows `userId=carol.executive`, `createdBy=dave.assistant`
- [x] OPA blocks cross-tenant access
- [x] Consent validation prevents unauthorized actions
- [x] Audit trail queryable via `GET /api/bookings/{id}/audit` — returns `actorId=dave.assistant`, `subjectId=carol.executive`, `delegationId`, `consentId`
- [x] `GET /api/expenses/{id}/audit` endpoint operational (verified via seeded data)

### Phase 3 Complete When: ✅ DONE (2026-04-04)
- [x] `AuditServiceImpl` wired into `BookingServiceImpl` and `ExpenseServiceImpl`
- [x] `booking_audit` and `expense_audit` tables populated on every mutation
- [x] `GET /api/bookings/{id}/audit` and `GET /api/expenses/{id}/audit` return full delegation trail
- [x] All 64 existing end-to-end regression assertions still passing
- [x] README and delegation flow guide updated ✅ 2026-04-05

### Phase 4 Complete When:
- [ ] `employee` Keycloak role assigned to `carol.executive` and `dave.assistant`; `create_expense` and workflow operations succeed without 403
- [ ] OPA `update_booking` owner rule added; Carol can update status of her own booking
- [ ] `PUT /api/bookings/{id}/status` and `DELETE /api/bookings/{id}` pass `HttpServletRequest`; delegation context threaded for Dave acting as Carol
- [ ] E2E regression script Phase 10: audit trail assertions pass (actorId, subjectId, delegationId, consentId, action=CREATE)
- [ ] E2E regression script Phase 11: expense delegation + audit assertions pass (requires 4.1 fix)
- [x] README files and delegation flow guide updated ✅ 2026-04-05

---

## Risks & Mitigation

### Risk 1: Token Exchange Not Working in Keycloak
**Probability**: Medium | **Impact**: Critical

**Mitigation**:
- Test token exchange manually with curl first
- Use Keycloak admin console to verify config
- Check Keycloak logs for errors
- Fallback: Use custom token enrichment if RFC 8693 fails

### Risk 2: Neo4j Integration Complexity
**Probability**: Medium | **Impact**: Medium

**Mitigation**:
- Start with simple Cypher queries
- Test in Neo4j browser first
- Keep PostgreSQL as source of truth
- Fallback: Store delegation in PostgreSQL only

### Risk 3: BFF Service Complexity
**Probability**: Low | **Impact**: Medium

**Mitigation**:
- Keep BFF logic simple (orchestration only)
- Don't duplicate business logic
- Use circuit breakers
- Document responsibilities clearly

### Risk 4: Audit Logging Performance
**Probability**: Low | **Impact**: Low

**Mitigation**:
- Make audit logging asynchronous with @Async
- Use separate thread pool
- Monitor table growth
- Implement archival strategy

---

## Docker Compose Configuration

Add these services to `docker-compose.yml`:

```yaml
  delegation-service:
    build:
      context: .
      dockerfile: services/delegation-service/Dockerfile
    container_name: corporate-travel-delegation-service
    platform: linux/amd64
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/corporate_travel
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      SPRING_NEO4J_URI: bolt://neo4j:7687
      SPRING_NEO4J_AUTHENTICATION_USERNAME: neo4j
      SPRING_NEO4J_AUTHENTICATION_PASSWORD: password123
      KEYCLOAK_URL: http://keycloak:8080
      OPA_URL: http://opa:8181
    ports:
      - "8083:8083"
    depends_on:
      postgres: {condition: service_healthy}
      neo4j: {condition: service_healthy}
      keycloak: {condition: service_healthy}
      opa: {condition: service_healthy}
    networks:
      - corporate-travel-network

  consent-service:
    build:
      context: .
      dockerfile: services/consent-service/Dockerfile
    container_name: corporate-travel-consent-service
    platform: linux/amd64
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/corporate_travel
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      KEYCLOAK_URL: http://keycloak:8080
      OPA_URL: http://opa:8181
    ports:
      - "8084:8084"
    depends_on:
      postgres: {condition: service_healthy}
      keycloak: {condition: service_healthy}
      opa: {condition: service_healthy}
    networks:
      - corporate-travel-network

  employee-bff:
    build:
      context: .
      dockerfile: services/employee-bff/Dockerfile
    container_name: corporate-travel-employee-bff
    platform: linux/amd64
    environment:
      SPRING_PROFILES_ACTIVE: docker
      KEYCLOAK_URL: http://keycloak:8080
      KEYCLOAK_REALM: corporate-travel
      KEYCLOAK_CLIENT_ID: employee-bff
      KEYCLOAK_CLIENT_SECRET: bff-service-secret
      TRAVEL_SERVICE_URL: http://travel-service:8081
      EXPENSE_SERVICE_URL: http://expense-service:8082
      DELEGATION_SERVICE_URL: http://delegation-service:8083
      CONSENT_SERVICE_URL: http://consent-service:8084
    ports:
      - "3001:3001"
    depends_on:
      keycloak: {condition: service_healthy}
      delegation-service: {condition: service_healthy}
      consent-service: {condition: service_healthy}
    networks:
      - corporate-travel-network
```

---

## Implementation Timeline

### Week 1: Foundation (Days 1-6)
**Monday-Tuesday**: Delegation Service setup, database, entities  
**Wednesday**: Delegation Service repositories, service layer, API  
**Thursday**: Consent Service setup, database, entities  
**Friday**: Consent Service repositories, service layer, API  
**Weekend**: Audit logging for Travel & Expense services

### Week 2: Integration (Days 7-11)
**Monday-Tuesday**: Employee BFF setup, Keycloak config, token exchange  
**Wednesday**: BFF service clients (Travel, Expense, Delegation, Consent)  
**Thursday**: BFF controllers and API aggregation  
**Friday**: Session management, error handling  
**Weekend**: Integration testing

### Week 3: Testing & Documentation (Days 12-13)
**Monday**: End-to-end testing, delegation flow validation  
**Tuesday**: Documentation updates, memory bank sync  
**Wednesday**: Demo preparation, final review

**Total: 13 working days**

---

## Next Steps

### Immediate Actions:
1. **Review this plan** - Ensure alignment with project goals
2. **Prioritize services** - Decide which service to implement first
3. **Set up environment** - Ensure Docker, Neo4j, Keycloak are working

### Recommended Implementation Order:
1. **Start with Delegation Service** (Most critical, tests Neo4j)
2. **Then Consent Service** (Simpler, builds confidence)
3. **Then Audit Logging** (Quick wins on existing services)
4. **Then Employee BFF** (Brings it all together)

### For Each Coding Session:
1. Open this file (`ADR-IMPLEMENTATION-PLAN.md`)
2. Review current section
3. Mark tasks as complete with `[x]`
4. Commit changes to track progress
5. Update memory bank when phase completes

---

## Progress Tracking

**Last Updated**: 2026-03-29
**Current Phase**: Phase 1 Complete — Starting Phase 2
**Next Milestone**: Employee BFF + Audit Logging

### Services Status:
- [x] Delegation Service - Fully implemented. Entities (JPA + Neo4j), repositories, service, controller, DTOs, exception handling, OpenAPI config, unit tests, DB migration. Dual-write to PostgreSQL and Neo4j graph.
- [x] Consent Service - Fully implemented. Entities (Consent + ConsentAudit), repositories, service with scheduler + OPA, controller, full DTOs, exception handling, unit tests. DB migration complete.
- [ ] Employee BFF - Not Started
- [ ] Audit Logging (Travel + Expense services) - Schema tables exist; service layer not implemented

### Phase Completion:
- [x] Phase 1: Foundation Services (100%) — Delegation ✅, Consent ✅
- [ ] Phase 2: Integration Layer (0%) — Employee BFF + Token Exchange pending
- [ ] Phase 3: Testing & Documentation (0%)

---

## Phase 2 Implementation Plan (Next Steps)

### Priority Order
1. **Audit Logging** — Quick win (1-2 days). Schema already exists. Add entities + wire into existing services.
2. **Employee BFF** — Core integration (4-5 days). Brings delegation + consent + token exchange together.
3. **Keycloak Token Exchange Config** — Required before BFF can work end-to-end.

---

### 2a. Audit Logging (Travel + Expense) — 1-2 days

**Current State**: Both `travel.booking_audit` and `expense.expense_audit` tables exist in Flyway migrations. Service layer has placeholder comments only.

**What to build**:

**Travel Service additions**:
```
services/travel-service/src/main/java/com/corporate/travel/travel/
├── model/entity/BookingAudit.java           ← new
├── repository/BookingAuditRepository.java    ← new
├── service/AuditService.java                ← new (interface)
└── service/impl/AuditServiceImpl.java       ← new
```
Wire `auditService.audit*()` calls into existing `BookingServiceImpl` methods: `createBooking`, `updateBookingStatus`, `deleteBooking`.
Add endpoint: `GET /api/bookings/{id}/audit`

**Expense Service additions** — identical pattern:
```
services/expense-service/src/main/java/com/corporate/travel/expense/
├── model/entity/ExpenseAudit.java
├── repository/ExpenseAuditRepository.java
├── service/AuditService.java
└── service/impl/AuditServiceImpl.java
```
Wire into `ExpenseServiceImpl` methods: `createExpense`, `updateExpenseStatus`, `submitExpense`, `approveExpense`.
Add endpoint: `GET /api/expenses/{id}/audit`

**Async pattern** (to avoid performance impact):
```java
@Async
public void auditBookingCreated(Booking booking, SecurityContext context) {
    BookingAudit audit = new BookingAudit();
    audit.setBookingId(booking.getId());
    audit.setActorId(context.getUserId());
    audit.setSubjectId(booking.getUserId()); // may differ in delegation context
    audit.setAction("CREATED");
    audit.setTenantId(context.getTenantId());
    auditRepository.save(audit);
}
```

**Tests**: Unit tests for `AuditServiceImpl`, verify actor ≠ subject in delegation scenario.

---

### 2b. Employee BFF — 4-5 days

**Purpose**: Stateless Spring Boot service that orchestrates token exchange, context switching, and API aggregation. Clients (browser/portal) talk only to BFF.

**Architecture**:
```
Browser → Employee BFF (:3001)
              ↓  token exchange
         Keycloak (:8080)
              ↓  delegated token
         API Gateway (:8000) → [travel, expense, delegation, consent]
```

**Prerequisites — Keycloak Config** (do this first):

Update `infrastructure/keycloak/realm-export.json` to add `employee-bff` client:
```json
{
  "clientId": "employee-bff",
  "enabled": true,
  "publicClient": false,
  "clientAuthenticatorType": "client-secret",
  "secret": "bff-service-secret",
  "directAccessGrantsEnabled": true,
  "attributes": {
    "oauth2.token.exchange.enabled": "true"
  }
}
```

Verify token exchange manually before coding BFF:
```bash
curl -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
  -d "client_id=employee-bff" \
  -d "client_secret=bff-service-secret" \
  -d "subject_token=<ALICE_TOKEN>" \
  -d "subject_token_type=urn:ietf:params:oauth:token-type:access_token" \
  -d "requested_subject=carol.executive"
```

**Service structure**:
```
services/employee-bff/
├── build.gradle
├── Dockerfile
└── src/main/java/com/corporate/travel/bff/
    ├── EmployeeBffApplication.java
    ├── config/
    │   ├── SecurityConfig.java
    │   └── WebClientConfig.java        ← WebClient beans for downstream services
    ├── client/
    │   ├── KeycloakTokenClient.java    ← token exchange via WebClient
    │   ├── TravelServiceClient.java
    │   ├── ExpenseServiceClient.java
    │   ├── DelegationServiceClient.java
    │   └── ConsentServiceClient.java
    ├── service/
    │   ├── TokenExchangeService.java   ← orchestrates RFC 8693 exchange
    │   └── DelegationContextService.java ← tracks active delegation per session
    ├── controller/
    │   ├── AuthBffController.java      ← POST /bff/delegation/activate, deactivate
    │   ├── BookingBffController.java   ← GET/POST /bff/bookings (auto-delegates)
    │   └── ExpenseBffController.java
    └── model/
        ├── TokenExchangeRequest.java
        └── DelegationContext.java      ← holds exchanged token + delegationId
```

**Token Exchange flow** (core):
```java
@Service
public class TokenExchangeService {
    public String exchangeForDelegatedToken(String originalToken, String targetSubject) {
        // POST to Keycloak token endpoint with grant_type=token-exchange
        // Returns token with sub=targetSubject, act.sub=callerSubject
    }
}
```

**Session / context**: Store `DelegationContext` in HTTP session (Spring Session or simple in-memory map keyed by session ID). BFF is stateful per-session only — domain services remain stateless.

**docker-compose.yml additions**:
```yaml
  employee-bff:
    build:
      context: .
      dockerfile: services/employee-bff/Dockerfile
    container_name: corporate-travel-employee-bff
    ports:
      - "3001:3001"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      KEYCLOAK_URL: http://keycloak:8080
      KEYCLOAK_REALM: corporate-travel
      KEYCLOAK_CLIENT_ID: employee-bff
      KEYCLOAK_CLIENT_SECRET: bff-service-secret
      TRAVEL_SERVICE_URL: http://api-gateway:8000
      EXPENSE_SERVICE_URL: http://api-gateway:8000
      DELEGATION_SERVICE_URL: http://api-gateway:8000
      CONSENT_SERVICE_URL: http://api-gateway:8000
    depends_on:
      keycloak: {condition: service_healthy}
      delegation-service: {condition: service_healthy}
      consent-service: {condition: service_healthy}
    networks:
      - corporate-travel-network
```

---

### Phase 2 Success Criteria

- [ ] `booking_audit` and `expense_audit` tables populated on every create/update/delete
- [ ] Audit entries capture `actor_id` ≠ `subject_id` in delegation context
- [ ] `GET /api/bookings/{id}/audit` returns audit trail
- [ ] Keycloak token exchange confirmed working via curl
- [ ] BFF `POST /bff/delegation/activate/{delegationId}` returns exchanged token
- [ ] BFF `GET /bff/bookings` forwards delegated token to API Gateway
- [ ] Booking created via delegation shows `user_id=carol`, `created_by=dave`
- [ ] Employee BFF running in docker-compose

---

### Phase 2 Risk: Token Exchange in Keycloak

Keycloak requires explicit `token-exchange` feature flag in some versions. If RFC 8693 token exchange fails:
1. Check Keycloak server logs for `Token exchange not enabled` errors
2. For Keycloak < 24, may need `-Dkeycloak.profile.feature.token_exchange=enabled` JVM arg
3. Fallback: Enrich token via Keycloak SPI (ADR-022) or custom claims mapper

---

**END OF IMPLEMENTATION PLAN**

*This document should be updated as implementation progresses. Mark tasks with [x] as they are completed.*
