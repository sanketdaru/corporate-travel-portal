# Implementation Guide

This document provides a detailed guide for implementing and extending the Corporate Travel & Expense platform.

## 🏗️ Current Implementation Status

*Last updated: 2026-03-29*

### ✅ Completed

- [x] Monorepo structure with Gradle multi-project build
- [x] Docker Compose infrastructure setup (Keycloak, PostgreSQL, Neo4j, OPA)
- [x] Database schemas and initialization scripts
- [x] Keycloak realm configuration with test users and clients
- [x] OPA authorization policies (Rego)
- [x] Shared security library (JWT handling, OPA client, security config)
- [x] Shared domain models (enums and common types)
- [x] Comprehensive README documentation
- [x] Travel Service — bookings CRUD, OPA authorization, unit tests
- [x] Expense Service — expense + items CRUD, workflow, unit tests
- [x] API Gateway — Spring Cloud Gateway with JWT validation and routing
- [x] Delegation Service — JPA + Neo4j dual-write, graph chain traversal, unit tests
- [x] Consent Service — grant/validate/revoke, purpose binding, audit trail, scheduler, unit tests

### 🚧 In Progress / To Be Implemented

- [ ] Audit Logging — `booking_audit` and `expense_audit` schemas exist; service layer not wired
- [ ] Employee BFF — Token exchange (RFC 8693) + API aggregation + session context
- [ ] Keycloak token exchange config — Standard Token Exchange V2 via Client Policies + Client Profiles on the realm (do NOT use deprecated per-client `oauth2.token.exchange.enabled` flag)

### ⏸️ Deferred (Post-MVP)

- [ ] Approval Service (workflow engine)
- [ ] Employee Portal (Next.js frontend)
- [ ] Keycloak SPI for token enrichment
- [ ] OpenTelemetry observability setup
- [ ] HashiCorp Vault
- [ ] Kubernetes deployment

## 📝 Implementation Patterns

### Service Implementation Pattern

Each microservice follows this standard structure:

```
services/{service-name}/
├── build.gradle
├── Dockerfile
├── src/
│   ├── main/
│   │   ├── java/com/corporate/travel/{service}/
│   │   │   ├── {Service}Application.java
│   │   │   ├── config/
│   │   │   │   └── ServiceConfig.java
│   │   │   ├── controller/
│   │   │   │   └── {Entity}Controller.java
│   │   │   ├── service/
│   │   │   │   ├── {Entity}Service.java
│   │   │   │   └── impl/{Entity}ServiceImpl.java
│   │   │   ├── repository/
│   │   │   │   └── {Entity}Repository.java
│   │   │   ├── model/
│   │   │   │   ├── entity/{Entity}.java
│   │   │   │   └── dto/{Entity}DTO.java
│   │   │   └── exception/
│   │   │       └── {Service}Exception.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-docker.yml
│   └── test/
│       └── java/com/corporate/travel/{service}/
└── README.md
```

### Standard Dependencies (build.gradle)

```gradle
plugins {
    id 'org.springframework.boot'
    id 'java'
}

dependencies {
    // Shared libraries
    implementation project(':services:shared:security-commons')
    implementation project(':services:shared:domain-models')
    
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    
    // Database
    runtimeOnly 'org.postgresql:postgresql'
    
    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}
```

### Application Configuration Template

```yaml
# application.yml
spring:
  application:
    name: {service-name}
  
  datasource:
    url: jdbc:postgresql://localhost:5432/corporate_travel
    username: admin
    password: admin123
    
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: {schema-name}
  
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/corporate-travel

server:
  port: 8080

# OPA Configuration
opa:
  url: http://localhost:8181

# Logging
logging:
  level:
    com.corporate.travel: DEBUG
    org.springframework.security: DEBUG
```

## 🔨 Building a Service: Step-by-Step

### Example: Travel Service Implementation

#### 1. Create Service Structure

```bash
mkdir -p services/travel-service/src/main/java/com/corporate/travel/travel
mkdir -p services/travel-service/src/main/resources
mkdir -p services/travel-service/src/test/java/com/corporate/travel/travel
```

#### 2. Create build.gradle

```gradle
plugins {
    id 'org.springframework.boot'
    id 'java'
}

dependencies {
    implementation project(':services:shared:security-commons')
    implementation project(':services:shared:domain-models')
    
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    
    runtimeOnly 'org.postgresql:postgresql'
    
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}

bootJar {
    archiveFileName = 'travel-service.jar'
}
```

#### 3. Create Main Application Class

```java
package com.corporate.travel.travel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.corporate.travel.travel",
    "com.corporate.travel.security"  // Scan shared security package
})
public class TravelServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelServiceApplication.class, args);
    }
}
```

#### 4. Create Entity

```java
package com.corporate.travel.travel.model.entity;

import com.corporate.travel.models.BookingStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings", schema = "travel")
@Data
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "booking_type", nullable = false)
    private String bookingType;  // FLIGHT, HOTEL, CAR
    
    @Column(name = "destination")
    private String destination;
    
    @Column(name = "start_date")
    private LocalDate startDate;
    
    @Column(name = "end_date")
    private LocalDate endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;
    
    @Column(name = "total_amount")
    private BigDecimal totalAmount;
    
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "updated_by")
    private String updatedBy;
}
```

#### 5. Create Repository

```java
package com.corporate.travel.travel.repository;

import com.corporate.travel.travel.model.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByTenantIdAndUserId(String tenantId, String userId);
    List<Booking> findByTenantId(String tenantId);
}
```

#### 6. Create Service

```java
package com.corporate.travel.travel.service;

import com.corporate.travel.models.BookingStatus;
import com.corporate.travel.security.SecurityContext;
import com.corporate.travel.travel.model.entity.Booking;

import java.util.List;
import java.util.UUID;

public interface BookingService {
    Booking createBooking(Booking booking, SecurityContext context);
    Booking getBooking(UUID id, SecurityContext context);
    List<Booking> getUserBookings(SecurityContext context);
    Booking updateBookingStatus(UUID id, BookingStatus status, SecurityContext context);
}
```

#### 7. Create Controller

```java
package com.corporate.travel.travel.controller;

import com.corporate.travel.security.JwtAuthenticationConverter;
import com.corporate.travel.security.SecurityContext;
import com.corporate.travel.travel.model.entity.Booking;
import com.corporate.travel.travel.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {
    
    private final BookingService bookingService;
    
    @PostMapping
    public ResponseEntity<Booking> createBooking(
            @RequestBody Booking booking,
            @AuthenticationPrincipal Jwt jwt) {
        
        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt);
        Booking created = bookingService.createBooking(booking, context);
        return ResponseEntity.ok(created);
    }
    
    @GetMapping
    public ResponseEntity<List<Booking>> getBookings(@AuthenticationPrincipal Jwt jwt) {
        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt);
        return ResponseEntity.ok(bookingService.getUserBookings(context));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBooking(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        
        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt);
        return ResponseEntity.ok(bookingService.getBooking(id, context));
    }
}
```

#### 8. Create Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/travel-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## 🔐 Authorization Integration

### Using OPA in Service Layer

```java
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    
    private final BookingRepository repository;
    private final OpaClient opaClient;
    
    @Override
    public Booking getBooking(UUID id, SecurityContext context) {
        Booking booking = repository.findById(id)
            .orElseThrow(() -> new BookingNotFoundException(id));
        
        // Build resource context for OPA
        Map<String, Object> resource = Map.of(
            "type", "booking",
            "id", booking.getId().toString(),
            "tenant_id", booking.getTenantId(),
            "user_id", booking.getUserId()
        );
        
        // Check authorization with OPA
        boolean allowed = opaClient.authorize(context, "view_booking", resource);
        
        if (!allowed) {
            throw new AccessDeniedException("Not authorized to view this booking");
        }
        
        return booking;
    }
}
```

## 🧪 Testing

### Integration Test Example

```java
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/corporate_travel_test",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/corporate-travel"
})
class BookingControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockUser(username = "alice.employee", roles = {"EMPLOYEE"})
    void createBooking_Success() throws Exception {
        String bookingJson = """
            {
                "bookingType": "FLIGHT",
                "destination": "New York",
                "startDate": "2024-06-01",
                "endDate": "2024-06-05",
                "totalAmount": 500.00
            }
            """;
        
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("New York"));
    }
}
```

## 🚀 Deployment

### Building Docker Images

```bash
# Build service JAR
./gradlew :services:travel-service:build

# Build Docker image
docker build -t corporate-travel/travel-service:latest \
    -f services/travel-service/Dockerfile \
    --build-context . \
    services/travel-service/
```

### Running Services

```bash
# Infrastructure first
docker-compose up -d postgres neo4j keycloak opa

# Wait for health checks
sleep 30

# Start application services
docker-compose up -d travel-service expense-service
```

## 📋 Next Steps

### Priority 1: Core Services

1. **Complete Travel Service** - Finish service implementation and testing
2. **Implement Expense Service** - Similar pattern to Travel Service
3. **Implement Approval Service** - Workflow state machine

### Priority 2: Supporting Services

4. **Implement Delegation Service** - Neo4j integration
5. **Implement Consent Service** - Consent management
6. **Create API Gateway** - Spring Cloud Gateway configuration

### Priority 3: Frontend

7. **Employee BFF** - Token exchange and API aggregation
8. **Employee Portal** - Next.js application with shadcn/ui

### Priority 4: Advanced Features

9. **Keycloak SPI** - Token enrichment
10. **OpenTelemetry** - Distributed tracing
11. **Service Mesh** - Consider Istio for advanced scenarios

## 🔍 Troubleshooting

### Common Issues

**Issue**: Services can't connect to Keycloak
- **Solution**: Ensure Keycloak is healthy: `docker-compose ps keycloak`
- Check realm is imported: http://localhost:8080/admin

**Issue**: OPA authorization always denies
- **Solution**: Test OPA policies directly with curl
- Check OPA logs: `docker-compose logs opa`

**Issue**: Database connection fails
- **Solution**: Verify PostgreSQL is running and schemas exist
- Check connection string in application.yml

**Issue**: JWT validation fails
- **Solution**: Verify issuer-uri matches Keycloak realm
- Check token expiration and signature

### Debugging Tips

1. **Enable Debug Logging**:
   ```yaml
   logging:
     level:
       com.corporate.travel: DEBUG
       org.springframework.security: DEBUG
   ```

2. **Test Keycloak Token**:
   ```bash
   curl -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
     -d "client_id=employee-portal" \
     -d "username=alice.employee" \
     -d "password=password123" \
     -d "grant_type=password"
   ```

3. **Test OPA Policy**:
   ```bash
   curl -X POST http://localhost:8181/v1/data/corporate/travel/authorization \
     -H "Content-Type: application/json" \
     -d @test-input.json
   ```

## 📚 Additional Resources

- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Open Policy Agent Documentation](https://www.openpolicyagent.org/docs/latest/)
- [OAuth 2.0 Token Exchange RFC 8693](https://datatracker.ietf.org/doc/html/rfc8693)
- [Project ADRs](./architecture-decision-records/)

## 💡 Best Practices

1. **Always validate tenant isolation** in authorization checks
2. **Log actor and subject** for all delegated actions
3. **Use OPA for authorization decisions** (don't hard-code in services)
4. **Implement comprehensive audit trails** for compliance
5. **Test delegation scenarios** thoroughly
6. **Keep services stateless** for horizontal scaling
7. **Use circuit breakers** for inter-service communication
8. **Implement health checks** for all services
9. **Document API contracts** with OpenAPI/Swagger
10. **Follow 12-factor app principles**

---

**Last Updated**: 2024
**Status**: Foundation Complete, Services In Progress
