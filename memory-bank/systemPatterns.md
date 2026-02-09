# System Patterns

## Architecture Overview

```
┌─────────────────┐
│  Employee Portal│ (Next.js + React)
└────────┬────────┘
         │
    ┌────▼─────┐
    │ Employee │
    │   BFF    │ (Token Exchange, API Aggregation)
    └────┬─────┘
         │
    ┌────▼─────────────────────────────────┐
    │         API Gateway                   │ (JWT Validation, Routing)
    └────┬─────────────────────────────────┘
         │
    ┌────┴──────┬──────────┬──────────┬────────────┐
┌───▼───┐  ┌───▼───┐  ┌───▼────┐ ┌──▼──────┐ ┌──▼────────┐
│Travel │  │Expense│  │Approval│ │Consent  │ │Delegation │
└───┬───┘  └───┬───┘  └───┬────┘ └──┬──────┘ └──┬────────┘
    │          │          │         │            │
    └──────────┴──────────┴─────────┴────────────┘
                     │                    │
              ┌──────▼──────┐      ┌─────▼─────┐
              │  PostgreSQL │      │   Neo4j   │
              └─────────────┘      └───────────┘

         ┌──────────┐          ┌──────────┐
         │ Keycloak │          │   OPA    │
         │   IAM    │          │  Policy  │
         └──────────┘          └──────────┘
```

## Key Architectural Decisions

### 1. Microservices with BFF Pattern (ADR-006, ADR-018)

**Pattern**: Each client type has a dedicated Backend-for-Frontend

**Why**:
- Client-specific token handling
- API aggregation/composition
- Simplified client security logic

**Implementation**:
- Employee BFF handles token exchange for delegation
- BFF aggregates data from multiple services
- BFF provides session management

### 2. External Authorization with OPA (ADR-007, ADR-019)

**Pattern**: Policy Decision Point (PDP) external to application services

**Why**:
- Separation of concerns (business logic vs. authorization)
- Dynamic policy updates without code deployment
- Expressive policy language (Rego)

**Implementation**:
```java
// Services call OPA for authorization decisions
boolean allowed = opaClient.authorize(
    securityContext,
    "view_booking",
    resourceContext
);
```

**Policy Structure**:
- Multi-tenant isolation checks
- Role-based access control
- Delegation-aware authorization
- Consent validation

### 3. Single Realm Multi-Tenancy (ADR-003)

**Pattern**: One Keycloak realm with group-based tenant isolation

**Why**:
- Operational simplicity
- Shared delegation/federation features
- Demonstrates SaaS IAM complexity

**Implementation**:
- Tenants represented as Keycloak groups
- `tenant_id` in JWT claims
- OPA enforces tenant boundaries

### 4. OAuth 2.0 Token Exchange (ADR-004)

**Pattern**: RFC 8693 token exchange for delegation

**Why**:
- Industry standard
- Clear actor/subject separation
- Strong audit trail

**Implementation**:
```
Original Token (Alice) 
    ↓ Token Exchange
Delegated Token (Actor=Dave, Subject=Alice)
```

**Token Claims**:
- `sub`: Original subject (Alice)
- `act.sub`: Actor performing action (Dave)
- `consent_id`: Link to consent record
- `purpose`: Why delegation was granted

### 5. Graph-Based Delegation (ADR-010)

**Pattern**: Neo4j for relationship modeling

**Why**:
- Efficient relationship traversal
- Temporal delegation tracking
- Supports complex delegation chains

**Implementation**:
```cypher
// Delegation relationship
(dave:User)-[:CAN_ACT_AS {
    granted_at: timestamp,
    expires_at: timestamp,
    purpose: "book_travel"
}]->(carol:User)
```

### 6. Consent Management (ADR-005)

**Pattern**: External consent service with purpose binding

**Why**:
- Fine-grained delegation control
- Regulatory compliance (GDPR)
- Consent lifecycle management

**Implementation**:
- Consent linked to delegation
- Purpose and scope explicitly defined
- Revocation tracked in audit ledger

## Component Relationships

### Identity Flow

```
User Login → Keycloak → JWT Token
    ↓
Token includes: user_id, tenant_id, roles, attributes
    ↓
Service receives token → Extracts SecurityContext
    ↓
SecurityContext + Action + Resource → OPA
    ↓
OPA evaluates policies → Allow/Deny
```

### Delegation Flow

```
Carol grants delegation to Dave
    ↓
Delegation stored in Neo4j + metadata in PostgreSQL
    ↓
Dave logs in → Gets regular token
    ↓
Dave requests delegation → BFF calls token exchange
    ↓
Keycloak issues token with actor/subject claims
    ↓
Services see Actor=Dave, Subject=Carol
    ↓
Audit logs record both identities
```

## Service Patterns

### Standard Service Structure

```
Controller (REST API)
    ↓
Service Layer (Business Logic + OPA Authorization)
    ↓
Repository (JPA/Neo4j)
    ↓
Database (PostgreSQL/Neo4j)
```

### Security Integration

Every service includes:

1. **JWT Processing**: Extract claims from token
2. **SecurityContext**: Build user/tenant/delegation context
3. **OPA Client**: Call policy engine for authorization
4. **Audit Logging**: Record actor/subject for sensitive operations

### Shared Libraries

**security-commons**: Common security infrastructure
- `SecurityContext`: User/tenant/delegation context
- `JwtAuthenticationConverter`: JWT → Spring Security
- `OpaClient`: OPA integration
- `SecurityConfig`: Base Spring Security config

**domain-models**: Shared DTOs and enums
- Status enums (BookingStatus, ExpenseStatus, ApprovalStatus)
- Common value objects

## Data Patterns

### Multi-Tenant Data Isolation

Every table includes `tenant_id`:
```sql
CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,  -- Tenant isolation
    user_id VARCHAR(255) NOT NULL,    -- User ownership
    ...
);

CREATE INDEX idx_bookings_tenant ON bookings(tenant_id);
```

### Audit Pattern

Actor/Subject tracking:
```sql
CREATE TABLE booking_audit (
    id UUID PRIMARY KEY,
    booking_id UUID REFERENCES bookings(id),
    actor_id VARCHAR(255) NOT NULL,      -- Who did it
    subject_id VARCHAR(255) NOT NULL,    -- On whose behalf
    action VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP NOT NULL
);
```

## Critical Implementation Paths

### Path 1: Creating a Booking (with Delegation)

1. Dave requests booking on behalf of Carol
2. BFF performs token exchange → Gets delegated token
3. BFF calls Travel Service with delegated token
4. Travel Service extracts SecurityContext (actor=Dave, subject=Carol)
5. Travel Service calls OPA with delegation context
6. OPA validates: delegation active + consent valid + same tenant
7. Travel Service creates booking (user_id=Carol, created_by=Dave)
8. Audit entry created (actor=Dave, subject=Carol)

### Path 2: Multi-Tenant Isolation

1. Alice (tenant-a) tries to view Eve's booking (tenant-b)
2. Service extracts SecurityContext (tenant_id=tenant-a)
3. Service loads booking from database (tenant_id=tenant-b)
4. Service calls OPA with resource context
5. OPA checks: user.tenant_id == resource.tenant_id
6. OPA returns: deny
7. Service throws AccessDeniedException
8. Audit entry logs the attempt

### Path 3: Manager Approval

1. Bob views pending approvals
2. OPA validates: has role "manager"
3. Bob selects Alice's expense
4. OPA validates: Alice reports to Bob (manager chain)
5. Service updates approval workflow
6. Approval Service triggers state transition
7. Expense status updates to "APPROVED"

## Design Principles

1. **Security by Default**: Deny unless explicitly allowed
2. **Fail Closed**: Authorization failures block access
3. **Explicit Audit**: Log all identity-sensitive operations
4. **Stateless Services**: JWT carries all context
5. **Policy as Code**: OPA policies versioned in git
6. **Idempotent Operations**: Safe to retry
7. **Clear Boundaries**: Tenant isolation strictly enforced
