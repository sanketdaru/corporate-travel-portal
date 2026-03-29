# ADR Implementation Plan & Gap Analysis

**Created**: 2026-02-28  
**Status**: Planning Complete - Ready for Implementation  
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

### Current State (75% Complete) — Updated 2026-03-29

**✅ Fully Implemented (9 ADRs)**
- Infrastructure (Keycloak, PostgreSQL, Neo4j, OPA)
- Spring Boot services (Travel, Expense)
- API Gateway
- Multi-tenant isolation
- Flyway migrations
- **Delegation Service** ← newly complete (Neo4j + PostgreSQL dual-write)
- **Consent Service** ← newly complete (with audit + scheduler + OPA)

**⚠️ Partially Implemented (2 ADRs)**
- Audit tables (schema exists in Travel + Expense; service layer not wired)
- Identity brokering (Keycloak capable; token exchange not configured)

**❌ Not Implemented — MVP Blockers**
- **Employee BFF** (token exchange + API aggregation)
- **Audit Logging** service layer in Travel + Expense

**❌ Not Implemented — Post-MVP (deferred)**
- Frontend (Next.js), Vault, OpenTelemetry, Kubernetes, Keycloak SPI

### Remaining MVP Work

**Phase 2** (current): Integration Layer — 5-7 days
- Audit Logging in Travel + Expense services (1-2 days)
- Employee BFF + Token Exchange (4-5 days)

**Phase 3**: Testing & Documentation (2 days)

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
- **Status**: ⚠️ Partial (50%)
- **Implemented**: 
  - ✅ Microservices architecture
  - ✅ Domain-driven service boundaries
- **Missing**: 
  - ❌ Backend-for-Frontend services
  - ❌ employee-bff commented out
- **Impact**: HIGH - Token exchange cannot work

#### ADR-011: Comprehensive Audit and Compliance Ledger
- **Status**: ⚠️ Partial (40%)
- **Implemented**:
  - ✅ booking_audit table exists
  - ✅ expense_audit table exists
  - ✅ Actor/subject columns defined
- **Missing**:
  - ❌ No audit entity classes
  - ❌ No audit repositories
  - ❌ No service layer audit logging
  - ❌ Tables empty
- **Impact**: HIGH - Core compliance requirement
- **Evidence**: SQL comment: `-- NOTE: Audit logging not yet implemented in service layer`

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

#### ADR-004: OAuth 2.0 Token Exchange ⚠️ CRITICAL
- **Status**: ❌ Not Implemented
- **Required For**: Delegation identity (Dave acting as Carol)
- **Approach**: Standard Token Exchange V2 (RFC 8693) — Keycloak 24+ only
  - `subject_token` (actor's token) is **mandatory** — establishes chain of trust
  - `audience` scopes the issued token to the target resource server
  - `requested_subject` identifies the delegation target (Carol)
  - Client Policies at realm level control which clients may exchange tokens
- **Missing**:
  - No token exchange implementation
  - No RFC 8693 / Standard Token Exchange V2 integration with Keycloak
  - No actor/subject claim handling
  - No delegation token flow
  - No Keycloak Client Policy configuration for `employee-bff`
- **Impact**: CRITICAL - Core identity pattern blocked

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

#### ADR-018: BFF Strategy ⚠️ CRITICAL
- **Status**: ❌ Not Implemented
- **Required For**: Token exchange, API aggregation
- **Missing**:
  - No employee-bff service
  - No token exchange logic
  - No API aggregation
  - No session management
- **Impact**: CRITICAL - Required for delegation flow

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

**Purpose**: Backend-for-Frontend that performs OAuth 2.0 Token Exchange

**Architecture**:
```
User Browser → Employee BFF → Keycloak (Token Exchange)
                ↓
          API Gateway → Domain Services
```

**Token Exchange Flow (Standard Token Exchange V2)**:
1. User (Dave) logs in → Gets his own access token (`sub=dave`)
2. Dave selects "Act as Carol" in UI
3. BFF calls Keycloak token exchange endpoint, passing **Dave's token as `subject_token`** (chain of trust)
4. Keycloak validates Dave's token, checks Client Policy authorising `employee-bff` to exchange
5. Keycloak issues delegated token (`sub=carol`, `act.sub=dave`, `aud=travel-service`)
6. BFF uses delegated token for downstream calls

> **Security note**: The `subject_token` is mandatory and non-negotiable. It proves Dave is authenticated before any delegation is performed. This distinguishes Standard Token Exchange V2 from the deprecated "naked" exchange, which accepted only client credentials and a `requested_subject` — providing no chain of trust.

**Keycloak Configuration Update**:

Update `infrastructure/keycloak/realm-export.json`:

Standard Token Exchange V2 is controlled via **Client Policies** at the realm level, not via the deprecated per-client `oauth2.token.exchange.enabled` attribute.

```json
{
  "clients": [
    {
      "clientId": "employee-bff",
      "enabled": true,
      "clientAuthenticatorType": "client-secret",
      "secret": "bff-service-secret",
      "directAccessGrantsEnabled": false,
      "publicClient": false,
      "attributes": {
        "oauth2.device.authorization.grant.enabled": "false",
        "oidc.ciba.grant.enabled": "false"
      }
    }
  ],
  "clientProfiles": {
    "profiles": [
      {
        "name": "standard-token-exchange-profile",
        "description": "Enables Standard Token Exchange V2 (RFC 8693) for confidential BFF clients",
        "executors": [
          {
            "executor": "secure-token-exchange",
            "configuration": {}
          }
        ]
      }
    ]
  },
  "clientPolicies": {
    "policies": [
      {
        "name": "allow-bff-token-exchange",
        "description": "Allow employee-bff to perform Standard Token Exchange V2 with subject_token chain of trust",
        "enabled": true,
        "conditions": [
          {
            "condition": "client-access-type",
            "configuration": {
              "type": ["confidential"]
            }
          },
          {
            "condition": "client-roles",
            "configuration": {
              "roles": ["token-exchanger"],
              "is-not": false
            }
          }
        ],
        "profiles": ["standard-token-exchange-profile"]
      }
    ]
  }
}
```

**Project Structure**:
```
services/employee-bff/
├── src/main/java/com/corporate/travel/bff/
│   ├── EmployeeBffApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── WebClientConfig.java
│   │   └── ServiceClientsConfig.java
│   ├── client/
│   │   ├── KeycloakClient.java          # Token exchange
│   │   ├── TravelServiceClient.java
│   │   ├── ExpenseServiceClient.java
│   │   ├── DelegationServiceClient.java
│   │   └── ConsentServiceClient.java
│   ├── service/
│   │   ├── TokenExchangeService.java
│   │   ├── DelegationContextService.java
│   │   └── ApiAggregationService.java
│   ├── controller/
│   │   ├── BookingBffController.java
│   │   ├── ExpenseBffController.java
│   │   └── DelegationBffController.java
│   └── model/
│       ├── DelegationContext.java
│       └── TokenExchangeRequest.java
```

**Key Implementation - Token Exchange (Standard V2)**:
```java
@Service
public class KeycloakClient {

    /**
     * Performs Standard Token Exchange V2 (RFC 8693) to obtain a delegation token.
     *
     * Security contract:
     *   - actorToken (subject_token) is MANDATORY. It proves the actor's (Dave's) identity
     *     and establishes the chain of trust. Keycloak will reject requests without it.
     *     This is what distinguishes V2 from the deprecated naked token exchange.
     *   - audience scopes the resulting token to a specific resource server, preventing
     *     it from being replayed against unintended services.
     *   - requestedSubject (Carol's ID) requires the employee-bff client to hold the
     *     token-exchanger role and be covered by the Client Policy on the realm.
     *
     * @param actorToken   The actor's current access token (e.g. Dave's JWT) — chain of trust
     * @param delegationTargetUserId  The user being acted on behalf of (e.g. Carol's user ID)
     * @param targetAudience  The resource server this token will be used against (e.g. "travel-service")
     */
    public TokenExchangeResponse exchangeToken(
            String actorToken,
            String delegationTargetUserId,
            String targetAudience) {

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        // subject_token: the actor's existing token — mandatory chain of trust for V2
        formData.add("subject_token", actorToken);
        formData.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        formData.add("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");
        // audience: scopes the issued token to the specific downstream resource server
        formData.add("audience", targetAudience);
        // requested_subject: delegation target (impersonation) — requires Client Policy grant
        formData.add("requested_subject", delegationTargetUserId);

        return webClient.post()
            .uri(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(formData)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new TokenExchangeException(
                        "Token exchange rejected by Keycloak — verify subject_token validity and Client Policy: " + body))))
            .bodyToMono(TokenExchangeResponse.class)
            .block();
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

### Phase 3: Testing & Documentation (2 days)

#### 3.1 Integration Testing (1 day, 8 hours)
- End-to-end delegation flow testing
- Multi-tenant isolation verification
- Token exchange validation
- Consent validation testing

#### 3.2 Documentation Updates (1 day, 8 hours)
- Update memory bank files
- Update service README files
- Create delegation flow guide
- Update IMPLEMENTATION.md

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

### Employee BFF (32 hours)

- [ ] **Setup** (3h)
  - [ ] Create project structure
  - [ ] Add dependencies (WebClient, OAuth2 client)
  - [ ] Configure application.yml
  
- [ ] **Keycloak Config** (3h)
  - [ ] Update realm-export.json with Client Profiles and Client Policies (Standard Token Exchange V2)
  - [ ] Assign `token-exchanger` role to `employee-bff` client in Keycloak
  - [ ] Do NOT use deprecated `oauth2.token.exchange.enabled` per-client attribute
  - [ ] Test token exchange with curl — verify subject_token is required (400 without it)
  
- [ ] **Token Exchange** (5h)
  - [ ] Implement KeycloakClient with Standard Token Exchange V2 (subject_token + audience + requested_subject)
  - [ ] Implement TokenExchangeService (actor token → delegation token per target audience)
  - [ ] Add TokenExchangeException with clear Keycloak rejection messaging
  - [ ] Validate subject_token is present before calling Keycloak (fail-fast)
  
- [ ] **Service Clients** (6h)
  - [ ] Create TravelServiceClient (WebClient)
  - [ ] Create ExpenseServiceClient
  - [ ] Create DelegationServiceClient
  - [ ] Create ConsentServiceClient
  - [ ] Add circuit breakers
  
- [ ] **BFF Controllers** (6h)
  - [ ] Create BookingBffController
  - [ ] Create ExpenseBffController
  - [ ] Create DelegationBffController
  - [ ] Implement API aggregation
  
- [ ] **Session Management** (3h)
  - [ ] Implement delegation context tracking
  - [ ] Add context switching logic
  - [ ] Store active delegation in session
  
- [ ] **Error Handling** (2h)
  - [ ] Global exception handler
  - [ ] Token exchange failure handling
  
- [ ] **Testing** (4h)
  - [ ] Integration tests with WireMock
  - [ ] Test token exchange flow

---

### Audit Logging (12 hours)

- [ ] **Travel Service** (6h)
  - [ ] Create BookingAudit entity
  - [ ] Create BookingAuditRepository
  - [ ] Implement AuditService
  - [ ] Integrate into BookingServiceImpl
  - [ ] Add audit endpoint
  - [ ] Write tests
  
- [ ] **Expense Service** (6h)
  - [ ] Create ExpenseAudit entity
  - [ ] Create ExpenseAuditRepository
  - [ ] Implement AuditService
  - [ ] Integrate into ExpenseServiceImpl
  - [ ] Add audit endpoint
  - [ ] Write tests

---

### Docker & Integration (16 hours)

- [ ] **Docker Configuration** (4h)
  - [ ] Add delegation-service to docker-compose.yml
  - [ ] Add consent-service to docker-compose.yml
  - [ ] Add employee-bff to docker-compose.yml
  - [ ] Create Dockerfiles for new services
  - [ ] Test docker-compose up
  
- [ ] **End-to-End Testing** (8h)
  - [ ] Test delegation creation flow
  - [ ] Test consent grant/validate flow
  - [ ] Test token exchange flow
  - [ ] Test Dave booking as Carol
  - [ ] Verify audit trail
  - [ ] Test multi-tenant isolation
  
- [ ] **Documentation** (4h)
  - [ ] Update memory bank
  - [ ] Update README files
  - [ ] Create delegation guide
  - [ ] Update IMPLEMENTATION.md

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

### Phase 2 Complete When:
- [ ] Token Exchange: Can exchange token with Keycloak
- [ ] BFF: Can switch to delegation mode
- [ ] BFF: Aggregates data from domain services
- [ ] Integration: BFF → Delegation → Consent → Domain Services works
- [ ] Actor/Subject: Tokens include act.sub claim
- [ ] OPA: Validates delegation context correctly

### End-to-End Demo Works:
- [ ] Carol grants delegation to Dave
- [ ] Dave logs in and activates delegation
- [ ] Dave creates booking as Carol
- [ ] Booking shows user_id=carol, created_by=dave
- [ ] Audit trail shows actor=dave, subject=carol
- [ ] OPA blocks cross-tenant access
- [ ] Consent validation prevents unauthorized actions

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
