# Technical Context

## Technology Stack

### Backend Services
- **Framework**: Spring Boot 3.2.2
- **Language**: Java 17
- **Build Tool**: Gradle 8.5 (multi-project build)
- **Security**: Spring Security OAuth2 Resource Server
- **ORM**: Spring Data JPA with Hibernate

### Frontend
- **Framework**: Next.js 14 (planned)
- **Language**: TypeScript
- **UI Library**: React 18
- **Design System**: shadcn/ui with Tailwind CSS
- **Auth**: NextAuth.js with Keycloak provider

### Infrastructure
- **IAM**: Keycloak 23.0
- **Relational DB**: PostgreSQL 15
- **Graph DB**: Neo4j 5.15 Community
- **Policy Engine**: Open Policy Agent (latest)
- **Container**: Docker + Docker Compose
- **Orchestration** (future): Kubernetes

## Development Setup

### Prerequisites
```bash
- Docker 24.0+
- Docker Compose 2.0+
- Java 17+ (for local development)
- Node.js 18+ (for frontend)
- Gradle 8.0+ (wrapper provided)
```

### Repository Structure
```
corporate-travel-portal/           # Monorepo root
├── build.gradle                    # Root Gradle config
├── settings.gradle                 # Multi-project setup
├── gradlew                         # Gradle wrapper
├── docker-compose.yml              # All services
├── .env.example                    # Environment template
│
├── infrastructure/                 # Infrastructure configs
│   ├── databases/
│   │   └── init-scripts/          # SQL initialization
│   ├── keycloak/
│   │   └── realm-export.json      # Keycloak config
│   └── opa/
│       └── policies/              # Rego policies
│
├── services/                       # All microservices
│   ├── shared/                    # Shared libraries
│   │   ├── security-commons/     # Security utils
│   │   └── domain-models/        # Shared models
│   ├── api-gateway/              # Spring Cloud Gateway
│   ├── travel-service/           # Domain service
│   ├── expense-service/          # Domain service
│   ├── approval-service/         # Workflow service
│   ├── consent-service/          # Consent management
│   ├── delegation-service/       # Delegation graph
│   └── employee-bff/             # Backend-for-Frontend
│
├── frontend/
│   └── employee-portal/          # Next.js app
│
├── scripts/                       # Utility scripts
│   ├── setup-local.sh            # Environment setup
│   ├── get-token.sh              # Token retrieval
│   ├── test-opa-policy.sh        # Policy testing
│   └── cleanup.sh                # Cleanup
│
└── docs/                          # Documentation
    ├── README.md                  # Main docs
    ├── IMPLEMENTATION.md          # Implementation guide
    └── GETTING-STARTED.md         # Quick start
```

## Key Dependencies

### Spring Boot Services
```gradle
dependencies {
    // Shared
    implementation project(':services:shared:security-commons')
    implementation project(':services:shared:domain-models')
    
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    
    // Database
    runtimeOnly 'org.postgresql:postgresql'
    
    // JWT & Auth
    implementation 'org.springframework.security:spring-security-oauth2-jose'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.3'
    
    // HTTP Client for OPA
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    
    // Utils
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

### Neo4j Integration (Delegation Service)
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-neo4j'
}
```

## Configuration Management

### Application Profiles

**application.yml** (Base):
```yaml
spring:
  application:
    name: ${SERVICE_NAME}
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: ${DB_SCHEMA}
  
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL}/realms/corporate-travel

server:
  port: 8080

opa:
  url: ${OPA_URL:http://localhost:8181}
```

**application-docker.yml** (Docker environment):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/corporate_travel
    username: admin
    password: admin123

keycloak:
  url: http://keycloak:8080
  
opa:
  url: http://opa:8181
```

### Environment Variables
```bash
# Database
POSTGRES_USER=admin
POSTGRES_PASSWORD=admin123
POSTGRES_DB=corporate_travel

# Neo4j
NEO4J_AUTH=neo4j/password123

# Keycloak
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin123

# Application
SPRING_PROFILES_ACTIVE=docker
```

## Technical Constraints

### Database
- **PostgreSQL**: Each service uses separate schema for isolation
- **Neo4j**: Single database for delegation graph
- **Transactions**: JPA transactions at service level
- **Migrations**: SQL init scripts (Flyway/Liquibase for production)

### Authentication & Authorization
- **Token Format**: JWT (RS256 signed by Keycloak)
- **Token Lifetime**: 5 minutes (configurable)
- **Refresh Tokens**: 30 minutes
- **Session**: Stateless (JWT only)
- **Authorization**: External (OPA), not embedded in Keycloak

### API Design
- **Style**: RESTful with JSON
- **Versioning**: URL-based (/api/v1/...)
- **Error Format**: RFC 7807 Problem Details
- **CORS**: Configured per service

### Performance Targets
- **API Response**: < 200ms (p95)
- **OPA Evaluation**: < 10ms (p95)
- **Database Query**: < 50ms (p95)
- **Token Validation**: < 5ms (cached public key)

## Development Workflow

### Local Development
```bash
# 1. Start infrastructure
./scripts/setup-local.sh

# 2. Run service locally
./gradlew :services:travel-service:bootRun

# 3. Test with token
./scripts/get-token.sh alice.employee
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/bookings
```

### Building Services
```bash
# Build all
./gradlew build

# Build specific service
./gradlew :services:travel-service:build

# Run tests
./gradlew test

# Build Docker image
docker build -t travel-service -f services/travel-service/Dockerfile .
```

### Testing Strategy
- **Unit Tests**: JUnit 5, Mockito
- **Integration Tests**: Spring Boot Test with Testcontainers
- **Security Tests**: Spring Security Test
- **OPA Tests**: Direct policy testing with curl
- **E2E Tests**: Postman/Newman collections (planned)

## Tool Usage Patterns

### Gradle Commands
```bash
# List projects
./gradlew projects

# Build without tests
./gradlew build -x test

# Clean and rebuild
./gradlew clean build

# Run specific test
./gradlew :services:travel-service:test --tests BookingServiceTest
```

### Docker Compose Commands
```bash
# Start all services
docker-compose up -d

# Start specific service
docker-compose up -d postgres keycloak

# View logs
docker-compose logs -f travel-service

# Restart service
docker-compose restart travel-service

# Stop all
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

### Database Access
```bash
# PostgreSQL
docker-compose exec postgres psql -U admin -d corporate_travel

# Neo4j
docker-compose exec neo4j cypher-shell -u neo4j -p password123
```

### Keycloak Management
```bash
# Access admin console
http://localhost:8080/admin

# Get token
./scripts/get-token.sh [username] [password]

# Test token exchange (manual)
curl -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
  -d "subject_token=$ORIGINAL_TOKEN" \
  -d "requested_token_type=urn:ietf:params:oauth:token-type:access_token" \
  -d "client_id=employee-bff" \
  -d "client_secret=bff-service-secret"
```

### OPA Testing
```bash
# Test policy
./scripts/test-opa-policy.sh

# Check OPA health
curl http://localhost:8181/health

# Query specific policy
curl -X POST http://localhost:8181/v1/data/corporate/travel/authorization/allow \
  -H "Content-Type: application/json" \
  -d @test-input.json
```

## Integration Points

### Service-to-Service Communication
- **Style**: Synchronous REST over HTTP
- **Auth**: OAuth2 client credentials (service accounts)
- **Discovery**: Direct DNS (Docker Compose), Kubernetes Service (K8s)
- **Circuit Breaker**: Resilience4j (planned)
- **Retry**: Spring Retry with exponential backoff

### External Systems
- **Keycloak**: OIDC endpoints for token operations
- **OPA**: REST API for policy decisions
- **PostgreSQL**: JDBC connections
- **Neo4j**: Bolt protocol connections

## Security Considerations

### Secrets Management
- **Current**: Environment variables and .env file
- **Production**: HashiCorp Vault (ADR-021, deferred)
- **Rotation**: Manual (automated in production)

### Network Security
- **Current**: Docker internal network
- **Production**: Service mesh (Istio) with mTLS
- **Firewall**: iptables (production)

### Data Protection
- **At Rest**: PostgreSQL encryption (production)
- **In Transit**: TLS 1.3 (production)
- **PII**: Encrypted columns (planned)

## Observability

### Logging
- **Format**: Structured JSON (Logstash format)
- **Level**: INFO (production), DEBUG (development)
- **Aggregation**: ELK stack (planned)

### Metrics
- **Actuator**: Spring Boot Actuator endpoints
- **Prometheus**: /actuator/prometheus (planned)
- **Dashboards**: Grafana (planned)

### Tracing
- **OpenTelemetry**: Planned (ADR-020)
- **Backend**: Jaeger (planned)
- **Correlation**: Trace ID in logs

## Deployment

### Current (Docker Compose)
```bash
docker-compose up -d
```

### Future (Kubernetes)
- Helm charts for each service
- ConfigMaps for configuration
- Secrets for credentials
- Ingress for routing
- HPA for scaling
