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

## Testing Patterns

### Unit Testing Strategy

**Framework**: JUnit 5 + Mockito + AssertJ

**Coverage Goals**:
- 80%+ line coverage
- 100% branch coverage
- All business logic paths tested
- Security integration points verified

### Test Organization

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceImpl Tests")
class ServiceImplTest {
    
    @Mock
    private Repository repository;
    
    @Mock
    private OpaClient opaClient;
    
    @InjectMocks
    private ServiceImpl service;
    
    @Nested
    @DisplayName("Operation Name Tests")
    class OperationNameTests {
        
        @Test
        @DisplayName("should_expectedBehavior_when_condition")
        void should_expectedBehavior_when_condition() {
            // Given - Setup test data
            // When - Execute operation
            // Then - Verify results
        }
    }
}
```

### Naming Conventions

**Test Class**: `{ServiceName}Test`
**Nested Class**: `{OperationName}Tests`
**Test Method**: `should_{expectedBehavior}_when_{condition}`

**Examples**:
- `should_createExpenseSuccessfully_when_validDataProvided`
- `should_throwAccessDenied_when_opaReturnsFalse`
- `should_useSubjectIdAsOwner_when_delegationPresent`

### Test Structure (AAA Pattern)

```java
@Test
void should_createResource_when_validInput() {
    // Given - Arrange: Set up test data and mocks
    Entity input = TestDataBuilder.anEntity().build();
    SecurityContext context = SecurityContextTestUtil.userContext();
    when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    
    // When - Act: Execute the operation
    Entity result = service.createResource(input, context);
    
    // Then - Assert: Verify results and interactions
    assertThat(result).isNotNull();
    assertThat(result.getTenantId()).isEqualTo(context.getTenantId());
    verify(opaClient).authorize(eq(context), eq("create_resource"), anyMap());
    verify(repository).save(any(Entity.class));
}
```

### Test Utilities Pattern

**Builder Pattern** for test 
```java
public class ExpenseTestDataBuilder {
    public static ExpenseBuilder anExpense() {
        return new ExpenseBuilder()
            .withId(UUID.randomUUID())
            .withTenantId("tenant-a")
            .withUserId("user-1")
            .withStatus(ExpenseStatus.DRAFT);
    }
}
```

**Fixtures** for common test data:
```java
public class ExpenseTestFixtures {
    public static final String ALICE_USER_ID = "alice-emp";
    public static final String TENANT_A = "tenant-a";
    
    public static Expense draftExpenseForAlice() {
        return ExpenseTestDataBuilder.anExpense()
            .withUserId(ALICE_USER_ID)
            .withTenantId(TENANT_A)
            .withStatus(ExpenseStatus.DRAFT)
            .build();
    }
}
```

**SecurityContext Utilities**:
```java
public class SecurityContextTestUtil {
    public static SecurityContext aliceContext() {
        return SecurityContext.builder()
            .userId("alice-emp")
            .tenantId("tenant-a")
            .roles(Set.of("employee"))
            .build();
    }
    
    public static SecurityContext createDelegatedContext(
            String actorId, String subjectId, String tenantId) {
        return SecurityContext.builder()
            .userId(actorId)
            .subjectId(subjectId)
            .tenantId(tenantId)
            .build();
    }
}
```

### Parameterized Tests

For testing all enum values or multiple scenarios:

```java
@ParameterizedTest
@EnumSource(ExpenseStatus.class)
@DisplayName("should_createWithAllStatuses_when_validStatus")
void should_createWithAllStatuses_when_validStatus(ExpenseStatus status) {
    // Given
    Expense input = ExpenseTestDataBuilder.anExpense()
        .withStatus(status)
        .build();
    
    // When
    Expense result = service.createExpense(input, context);
    
    // Then
    assertThat(result.getStatus()).isEqualTo(status);
}
```

### Mocking Strategy

**Mock Dependencies**: Repositories, OpaClient, external services
**Don't Mock**: Entities, DTOs, value objects
**Inject Mocks**: Service under test uses @InjectMocks

```java
@Mock
private ExpenseRepository expenseRepository;

@Mock
private OpaClient opaClient;

@InjectMocks
private ExpenseServiceImpl expenseService;
```

### AssertJ Fluent Assertions

```java
// Single assertion
assertThat(result).isNotNull();
assertThat(result.getStatus()).isEqualTo(ExpenseStatus.DRAFT);

// BigDecimal comparison
assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

// Collection assertions
assertThat(result.getItems()).hasSize(3);
assertThat(result.getItems()).contains(newItem);
assertThat(results).allMatch(e -> e.getTenantId().equals("tenant-a"));

// Exception assertions
assertThatThrownBy(() -> service.operation(invalid))
    .isInstanceOf(NotFoundException.class)
    .hasMessageContaining("not found");
```

### Verification Pattern

```java
// Verify method was called
verify(repository).save(any(Expense.class));

// Verify with specific arguments
verify(opaClient).authorize(eq(context), eq("create_expense"), anyMap());

// Verify method was NOT called
verify(repository, never()).delete(any());

// Verify call count
verify(repository, times(2)).findById(any());
```

### Test Coverage by Operation Type

**CRUD Operations** (per operation):
1. Happy path - successful operation
2. Authorization denied - OPA returns false
3. Not found - resource doesn't exist
4. Tenant isolation - cross-tenant access blocked
5. Delegation - actor/subject handling
6. Field validation - all fields tested
7. Status validation - workflow states enforced

**Example - Create Operation Tests**:
- should_createSuccessfully_when_validDataProvided
- should_setDefaultValues_when_notProvided
- should_setTenantIdFromContext_when_creating
- should_useSubjectIdAsOwner_when_delegationPresent
- should_useUserIdAsOwner_when_noDelegation
- should_throwAccessDenied_when_opaReturnsFalse
- should_setAuditFields_when_creating
- should_createWithAllStatuses_when_validStatus (parameterized)

### Security Testing Pattern

Every operation tests:
1. **OPA Authorization**: Call to OPA with correct action/resource
2. **Tenant Isolation**: Cross-tenant access blocked
3. **Delegation Handling**: Actor vs subject correctly assigned
4. **Audit Fields**: Created_by/updated_by set to actor

```java
@Test
void should_throwAccessDenied_when_opaReturnsFalse() {
    // Given
    when(opaClient.authorize(any(), any(), anyMap())).thenReturn(false);
    
    // When / Then
    assertThatThrownBy(() -> service.operation(input, context))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Not authorized");
    
    verify(opaClient).authorize(eq(context), eq("operation_name"), anyMap());
    verify(repository, never()).save(any());
}
```

## Design Principles

1. **Security by Default**: Deny unless explicitly allowed
2. **Fail Closed**: Authorization failures block access
3. **Explicit Audit**: Log all identity-sensitive operations
4. **Stateless Services**: JWT carries all context
5. **Policy as Code**: OPA policies versioned in git
6. **Idempotent Operations**: Safe to retry
7. **Clear Boundaries**: Tenant isolation strictly enforced

## Docker Best Practices

### Multi-Stage Build Pattern (3 Stages)

All services follow this standardized pattern for optimal image size, security, and build caching:

#### **Stage 1: Dependencies (Cached Layer)**
- **Purpose**: Download and cache Gradle dependencies separately from source code
- **Base Image**: `gradle:9.3-jdk17`
- **Benefits**: Fast rebuilds when only source code changes (dependencies layer cached)
- **Pattern**: 
  ```dockerfile
  FROM gradle:9.3-jdk17 AS dependencies
  COPY build files → Download dependencies → Cache in /home/gradle/.gradle
  ```

#### **Stage 2: Builder (Source Compilation)**
- **Purpose**: Compile application from source inside Docker
- **Base Image**: `gradle:9.3-jdk17`
- **Benefits**: Reproducible builds, eliminates "works on my machine" issues
- **Pattern**: 
  ```dockerfile
  FROM gradle:9.3-jdk17 AS builder
  Copy cached deps → Copy shared libs → Copy service source → Build JAR
  ```

#### **Stage 3: Runtime (Production Image)**
- **Purpose**: Minimal, secure production image
- **Base Image**: `eclipse-temurin:17-jre-jammy` (Ubuntu-based)
- **Why Jammy, not Alpine**: 
  - Better compatibility with Spring Boot native libraries
  - More stable for production workloads
  - Standard base for all services
- **Benefits**: Small size (~200MB), security hardened, optimized

### Security Best Practices

#### 1. Non-root User
**Critical for security** - All services run as non-root user:

```dockerfile
# Create dedicated user and group
RUN groupadd -r spring && useradd -r -g spring spring

# Set ownership of application files
COPY --from=builder --chown=spring:spring /app/service/build/libs/*.jar app.jar

# Switch to non-root user
USER spring:spring
```

**Rationale**:
- Prevents privilege escalation attacks
- Follows principle of least privilege
- Required by many Kubernetes security policies
- Best practice for container security

#### 2. Image Labels
**Enables tracking and automation**:

```dockerfile
LABEL maintainer="corporate-travel-platform"
LABEL service="service-name"
LABEL version="1.0.0"
LABEL org.opencontainers.image.source="https://github.com/corporate-travel-portal"
```

**Benefits**:
- Easy identification in container registries
- Automated scanning and compliance checks
- Version tracking
- Source code traceability

### JVM Optimization Flags

**Standard flags for all services** (optimized for container environments):

```bash
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"
```

**Flag Rationale**:

| Flag | Purpose | Benefit |
|------|---------|---------|
| `UseContainerSupport` | JVM respects container memory/CPU limits | Prevents OOM in Kubernetes |
| `MaxRAMPercentage=75.0` | Uses 75% of available RAM | Leaves 25% for OS and buffers |
| `InitialRAMPercentage=50.0` | Starts with 50% of max heap | Faster startup, gradual growth |
| `ExitOnOutOfMemoryError` | Terminates JVM on OOM | Orchestrator can restart clean |
| `UseG1GC` | G1 garbage collector | Better for heap >4GB and low-latency |
| `UseStringDeduplication` | Deduplicates strings in heap | Reduces memory footprint 10-20% |
| `java.security.egd` | Use /dev/urandom for random | Faster startup (not /dev/random) |
| `spring.backgroundpreinitializer.ignore` | Disable background init | Faster startup in containers |

### Health Check Configuration

**Standard pattern for all services**:

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:PORT/actuator/health || exit 1
```

**Parameters explained**:
- **start-period=60s**: Allows Spring Boot 60 seconds to initialize (not 40s)
- **interval=30s**: Check every 30 seconds after start period
- **timeout=3s**: Health check must respond within 3 seconds
- **retries=3**: Mark unhealthy after 3 consecutive failures

**Why 60s start period?**
- Spring Boot takes 30-45s to fully initialize
- Flyway migrations add 5-15s
- OPA and Keycloak connectivity checks add 5-10s
- 60s provides buffer for slow startup conditions

### Complete Dockerfile Template

**All services follow this exact pattern**:

```dockerfile
# Multi-stage build for [Service Name]
# Best practices: multi-platform support, layer caching, minimal image size, security

# Stage 1: Build dependencies (cached layer)
FROM gradle:9.3-jdk17 AS dependencies
WORKDIR /app
COPY services/[service-name]/settings-docker.gradle settings.gradle
COPY build.gradle gradle.properties ./
COPY services/shared/security-commons/build.gradle services/shared/security-commons/build.gradle
COPY services/shared/domain-models/build.gradle services/shared/domain-models/build.gradle
COPY services/[service-name]/build.gradle services/[service-name]/build.gradle
RUN gradle dependencies --no-daemon || return 0

# Stage 2: Build application
FROM gradle:9.3-jdk17 AS builder
WORKDIR /app
COPY --from=dependencies /home/gradle/.gradle /home/gradle/.gradle
COPY services/[service-name]/settings-docker.gradle settings.gradle
COPY build.gradle gradle.properties ./
COPY services/shared ./services/shared
COPY services/[service-name] ./services/[service-name]
RUN gradle :services:[service-name]:build -x test --no-daemon

# Stage 3: Runtime (minimal multi-platform image)
FROM eclipse-temurin:17-jre-jammy
LABEL maintainer="corporate-travel-platform"
LABEL service="[service-name]"
LABEL version="1.0.0"
LABEL org.opencontainers.image.source="https://github.com/corporate-travel-portal"

RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd -r spring && useradd -r -g spring spring
WORKDIR /app
COPY --from=builder --chown=spring:spring \
    /app/services/[service-name]/build/libs/*.jar app.jar

USER spring:spring
EXPOSE [port]

ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:[port]/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

### Build Context Requirements

**IMPORTANT**: Always build from repository root, not from service directory:

```bash
# ✅ CORRECT - Build from root
cd /path/to/corporate-travel-portal
docker build -t consent-service -f services/consent-service/Dockerfile .

# ❌ WRONG - Will fail (missing shared libraries)
cd /path/to/corporate-travel-portal/services/consent-service
docker build -t consent-service .
```

**Why?** Multi-stage build needs:
- Access to `services/shared/security-commons`
- Access to `services/shared/domain-models`
- Access to root `build.gradle` and `gradle.properties`
