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

### Current State (60% Complete)

**✅ Fully Implemented (7 ADRs - 30%)**
- Infrastructure (Keycloak, PostgreSQL, Neo4j, OPA)
- Spring Boot services (Travel, Expense)
- API Gateway
- Multi-tenant isolation
- Flyway migrations

**⚠️ Partially Implemented (4 ADRs - 17%)**
- Microservices (missing BFF)
- Audit tables (structure exists, logging not implemented)
- Neo4j (running but unused)
- Identity brokering (capability exists, not configured)

**❌ Not Implemented (12 ADRs - 53%)**
- **CRITICAL**: Token Exchange, Delegation Service, Consent Service, BFF
- Frontend (Next.js)
- Post-MVP: Vault, OpenTelemetry, Kubernetes, Keycloak SPI

### Implementation Priority

**Phase 1**: Foundation Services (5-6 days)
- Delegation Service
- Consent Service  
- Audit Logging

**Phase 2**: Integration Layer (4-5 days)
- Employee BFF
- Token Exchange Configuration

**Phase 3**: Testing & Documentation (2 days)
- Integration testing
- Documentation updates

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
- **Status**: ⚠️ Partial (30%)
- **Implemented**:
  - ✅ Neo4j 5.15 running
  - ✅ Ports accessible (7474/7687)
- **Missing**:
  - ❌ No delegation service
  - ❌ No graph queries
  - ❌ No Spring Data Neo4j integration
  - ❌ Database unused
- **Impact**: HIGH - Required for delegation

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
- **Missing**:
  - No token exchange implementation
  - No RFC 8693 integration with Keycloak
  - No actor/subject claim handling
  - No delegation token flow
- **Impact**: CRITICAL - Core identity pattern blocked

#### ADR-005: External Consent and Purpose Binding Service ⚠️ CRITICAL
- **Status**: ❌ Not Implemented
- **Required For**: Consent management and GDPR compliance
- **Missing**:
  - No consent-service implementation
  - No consent database schema
  - OPA has validation logic but nothing to validate
  - No consent lifecycle management
- **Impact**: HIGH - Required for compliant delegation

#### ADR-010: Persist Delegation Relationships Using Graph ⚠️ CRITICAL
- **Status**: ❌ Not Implemented
- **Required For**: Delegation storage and traversal
- **Missing**:
  - No delegation-service implementation
  - Neo4j unused
  - No graph schema
  - No Cypher queries
- **Impact**: HIGH - Cannot store delegations

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

**Token Exchange Flow**:
1. User logs in → Gets regular token (sub=dave)
2. User selects "Act as Carol" in UI
3. BFF calls Keycloak token exchange endpoint
4. Keycloak returns delegated token (sub=carol, act.sub=dave)
5. BFF uses delegated token for downstream calls

**Keycloak Configuration Update**:

Update `infrastructure/keycloak/realm-export.json`:
```json
{
  "clients": [
    {
      "clientId": "employee-bff",
      "enabled": true,
      "clientAuthenticatorType": "client-secret",
      "secret": "bff-service-secret",
      "directAccessGrantsEnabled": true,
      "publicClient": false,
      "attributes": {
        "oauth2.device.authorization.grant.enabled": "false",
        "oidc.ciba.grant.enabled": "false",
        "oauth2.token.exchange.enabled": "true"
      }
    }
  ]
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

**Key Implementation - Token Exchange**:
```java
@Service
public class KeycloakClient {
    
    public TokenExchangeResponse exchangeToken(String originalToken, String subjectUserId) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("subject_token", originalToken);
        formData.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        formData.add("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");
        formData.add("requested_subject", subjectUserId);
        
        return webClient.post()
            .uri(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(formData)
            .retrieve()
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

### Delegation Service (17 hours)

- [ ] **Setup** (2h)
  - [ ] Create project structure
  - [ ] Add dependencies to build.gradle
  - [ ] Configure application.yml (local + docker)
  
- [ ] **Database** (2h)
  - [ ] Create V1__baseline_delegations_schema.sql
  - [ ] Create Neo4j schema scripts
  - [ ] Test Flyway migration
  
- [ ] **Entities** (4h)
  - [ ] Create Delegation.java (JPA entity)
  - [ ] Create UserNode.java (Neo4j node)
  - [ ] Create DelegationRelationship.java (Neo4j relationship)
  - [ ] Create DTOs (Request/Response)
  
- [ ] **Repositories** (2h)
  - [ ] Create DelegationRepository.java (JPA)
  - [ ] Create DelegationGraphRepository.java (Neo4j)
  - [ ] Implement custom Cypher queries
  
- [ ] **Service Layer** (3h)
  - [ ] Implement DelegationServiceImpl
  - [ ] Add OPA authorization checks
  - [ ] Implement PostgreSQL ↔ Neo4j sync
  
- [ ] **REST API** (2h)
  - [ ] Create DelegationController
  - [ ] Add OpenAPI documentation
  - [ ] Add validation
  
- [ ] **Testing** (2h)
  - [ ] Write unit tests
  - [ ] Test Neo4j integration locally

---

### Consent Service (19 hours)

- [ ] **Setup** (2h)
  - [ ] Create project structure
  - [ ] Add dependencies
  - [ ] Configure application.yml
  
- [ ] **Database** (1h)
  - [ ] Create V1__baseline_consents_schema.sql
  - [ ] Test Flyway migration
  
- [ ] **Entities** (3h)
  - [ ] Create Consent.java
  - [ ] Create ConsentAudit.java
  - [ ] Create DTOs
  
- [ ] **Repositories** (2h)
  - [ ] Create ConsentRepository
  - [ ] Create ConsentAuditRepository
  - [ ] Add custom queries
  
- [ ] **Service Layer** (5h)
  - [ ] Implement ConsentServiceImpl
  - [ ] Add consent validation logic
  - [ ] Implement auto-expiry scheduler
  - [ ] Add OPA authorization
  
- [ ] **REST API** (2h)
  - [ ] Create ConsentController
  - [ ] Add OpenAPI documentation
  
- [ ] **Integration** (2h)
  - [ ] Link with Delegation Service
  - [ ] Test consent → delegation relationship
  
- [ ] **Testing** (2h)
  - [ ] Write unit tests
  - [ ] Test validation logic

---

### Employee BFF (32 hours)

- [ ] **Setup** (3h)
  - [ ] Create project structure
  - [ ] Add dependencies (WebClient, OAuth2 client)
  - [ ] Configure application.yml
  
- [ ] **Keycloak Config** (3h)
  - [ ] Update realm-export.json
  - [ ] Enable token exchange in Keycloak
  - [ ] Test token exchange with curl
  
- [ ] **Token Exchange** (5h)
  - [ ] Implement KeycloakClient
  - [ ] Implement TokenExchangeService
  - [ ] Add error handling
  
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

### Phase 1 Complete When:
- [ ] Delegation Service: Can create/query/revoke delegations
- [ ] Neo4j: Delegation relationships stored and queryable
- [ ] Consent Service: Can grant/validate/revoke consents
- [ ] PostgreSQL: delegation and consent schemas created
- [ ] Unit tests: 80%+ coverage for both services
- [ ] Docker: Both services running in docker-compose
- [ ] Audit: BookingAudit and ExpenseAudit tables populated

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

**Last Updated**: 2026-02-28  
**Current Phase**: Planning Complete  
**Next Milestone**: Start Phase 1 - Delegation Service

### Services Status:
- [ ] Delegation Service - Not Started
- [ ] Consent Service - Not Started  
- [ ] Employee BFF - Not Started
- [ ] Audit Logging - Not Started

### Phase Completion:
- [ ] Phase 1: Foundation Services (0%)
- [ ] Phase 2: Integration Layer (0%)
- [ ] Phase 3: Testing & Documentation (0%)

---

**END OF IMPLEMENTATION PLAN**

*This document should be updated as implementation progresses. Mark tasks with [x] as they are completed.*
