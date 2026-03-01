# Delegation Service

Manages delegation relationships using PostgreSQL and Neo4j graph database. Enables users to grant permissions to others to act on their behalf.

## Overview

The Delegation Service implements a dual-database architecture:
- **PostgreSQL**: Source of truth for delegation metadata (CRUD, audit, queries)
- **Neo4j**: Optimized for graph traversal (delegation chains, relationship queries)

### Key Features

- ✅ Create and manage delegations
- ✅ Multi-tenant isolation
- ✅ OPA-based authorization
- ✅ Graph traversal for delegation chains
- ✅ Temporal delegations with expiration
- ✅ Async sync between PostgreSQL and Neo4j
- ✅ Comprehensive OpenAPI/Swagger documentation

## Architecture

```
Client → Controller → Service → Repository → Database
                ↓
            OPA Client (Authorization)
                ↓
         PostgreSQL (Source of Truth)
                ↓
         Neo4j (Graph Queries) [Async Sync]
```

## API Endpoints

### Create Delegation
```http
POST /api/delegations
Authorization: Bearer <token>
Content-Type: application/json

{
  "delegateId": "dave-assistant",
  "purpose": "book_travel",
  "scopes": ["view_bookings", "create_bookings"],
  "expiresAt": "2026-12-31T23:59:59"
}
```

### Get My Delegations
```http
GET /api/delegations/my-delegations
Authorization: Bearer <token>
```

### Get Delegations To Me
```http
GET /api/delegations/to-me
Authorization: Bearer <token>
```

### Get Delegation By ID
```http
GET /api/delegations/{id}
Authorization: Bearer <token>
```

### Revoke Delegation
```http
DELETE /api/delegations/{id}
Authorization: Bearer <token>
```

### Get Delegation Chain
```http
GET /api/delegations/chain?userId=dave-assistant
Authorization: Bearer <token>
```

### Check Delegation Exists
```http
GET /api/delegations/check?delegatorId=carol&delegateId=dave
Authorization: Bearer <token>
```

## Configuration

### Local Development (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/corporate_travel
    username: admin
    password: admin123
  
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: password123
  
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/corporate-travel

server:
  port: 8083
```

### Docker (application-docker.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/corporate_travel
  neo4j:
    uri: bolt://neo4j:7687
```

## Database Schemas

### PostgreSQL Schema
```sql
CREATE TABLE delegation.delegations (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    delegator_id VARCHAR(255) NOT NULL,
    delegate_id VARCHAR(255) NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    scopes TEXT[] NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    ...
);
```

### Neo4j Graph Model
```cypher
// Nodes
(:User {userId, tenantId, email, displayName})

// Relationships
(:User)-[:CAN_ACT_AS {
  delegationId,
  grantedAt,
  expiresAt,
  purpose,
  scopes,
  active
}]->(:User)
```

## Running Locally

### Prerequisites
- Java 17+
- Docker (for PostgreSQL and Neo4j)
- Gradle 8.5+

### Start Infrastructure
```bash
# From project root
docker-compose up -d postgres neo4j keycloak opa
```

### Run Service
```bash
./gradlew :services:delegation-service:bootRun
```

### Access Swagger UI
```
http://localhost:8083/swagger-ui.html
```

## Running Tests

```bash
# Run all tests
./gradlew :services:delegation-service:test

# Run with coverage
./gradlew :services:delegation-service:jacocoTestReport

# View coverage report
open services/delegation-service/build/reports/jacoco/test/html/index.html
```

## Docker Deployment

### Build Image
```bash
docker build -t delegation-service -f services/delegation-service/Dockerfile .
```

### Run Container
```bash
docker run -p 8083:8083 \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/corporate_travel \
  -e SPRING_NEO4J_URI=bolt://neo4j:7687 \
  delegation-service
```

## Key Design Patterns

### Dual-Database Sync Pattern
1. **Write**: Save to PostgreSQL first (transactional)
2. **Sync**: Async sync to Neo4j (best effort)
3. **Read**: Query from most appropriate source
4. **Fallback**: Use PostgreSQL if Neo4j fails

### Multi-Tenant Isolation
- All queries filtered by `tenant_id` from JWT
- OPA enforces tenant boundaries
- Neo4j nodes include `tenantId` property

### OPA Authorization
Every operation checks OPA:
- `create_delegation`: Can user grant delegation?
- `view_delegations`: Can user view own delegations?
- `revoke_delegation`: Is user the delegator?
- `query_delegation_chain`: Can user query chain?

## Dependencies

- Spring Boot 3.2.2
- Spring Data JPA
- Spring Data Neo4j
- Spring Security OAuth2 Resource Server
- PostgreSQL Driver
- Flyway (database migrations)
- OpenAPI/Swagger
- Lombok

## Monitoring

### Health Check
```http
GET /actuator/health
```

### Metrics
```http
GET /actuator/metrics
```

## Troubleshooting

### Neo4j Connection Issues
```bash
# Check Neo4j is running
docker-compose ps neo4j

# Check logs
docker-compose logs neo4j

# Test connection
docker-compose exec neo4j cypher-shell -u neo4j -p password123
```

### PostgreSQL Connection Issues
```bash
# Check PostgreSQL is running
docker-compose ps postgres

# Check schema exists
docker-compose exec postgres psql -U admin -d corporate_travel -c "\dn"

# Check tables
docker-compose exec postgres psql -U admin -d corporate_travel -c "\dt delegation.*"
```

### Flyway Migration Issues
```bash
# Check migration history
docker-compose exec postgres psql -U admin -d corporate_travel -c "SELECT * FROM flyway_schema_history;"
```

## Related Services

- **Travel Service**: Uses delegations for booking operations
- **Expense Service**: Uses delegations for expense submission
- **Consent Service**: Links consent records to delegations
- **Employee BFF**: Performs token exchange using delegations

## License

Apache 2.0