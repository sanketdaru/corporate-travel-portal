# API Gateway Service

Spring Cloud Gateway implementation providing centralized routing, security, and traffic management for the Corporate Travel & Expense Platform.

## Overview

The API Gateway serves as the single entry point for all client requests to backend microservices. It provides:

- **JWT Token Validation**: Validates tokens once at the gateway level
- **Path-Based Routing**: Routes requests to appropriate backend services
- **Security Headers**: Adds security headers to all responses
- **CORS Configuration**: Centralized CORS handling
- **Circuit Breakers**: Resilience patterns for backend service failures
- **Request/Response Logging**: Comprehensive logging for monitoring

## Architecture

```
Client (Browser/Mobile/BFF)
    ↓
    ↓ HTTP/HTTPS with JWT
    ↓
API Gateway (Port 8000)
    ├─ JWT Validation (Spring Security OAuth2)
    ├─ Route Matching
    ├─ Filters (Logging, Security Headers)
    └─ Circuit Breakers (Resilience4j)
    ↓
Backend Services
    ├─ Travel Service (Port 8081)
    ├─ Expense Service (Port 8082)
    ├─ Approval Service (Port 8083)
    ├─ Delegation Service (Port 8084)
    └─ Consent Service (Port 8085)
```

## Routes

### Current Routes

| Path | Backend Service | Description |
|------|----------------|-------------|
| `/api/travel/**` | travel-service:8081 | Booking management |
| `/api/expenses/**` | expense-service:8082 | Expense management |

### Future Routes

| Path | Backend Service | Description |
|------|----------------|-------------|
| `/api/approvals/**` | approval-service:8083 | Approval workflows |
| `/api/delegations/**` | delegation-service:8084 | Delegation management |
| `/api/consent/**` | consent-service:8085 | Consent management |

## Features

### 1. JWT Authentication

All requests (except health checks) require a valid JWT token:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8000/api/travel/bookings
```

### 2. Circuit Breakers

Each backend service has a dedicated circuit breaker with fallback responses:

- **Sliding Window**: 10 requests
- **Failure Threshold**: 50%
- **Wait Duration**: 10 seconds
- **Half-Open Calls**: 3

### 3. Security Headers

All responses include:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Strict-Transport-Security: max-age=31536000`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: geolocation=(), microphone=(), camera=()`

### 4. CORS Configuration

CORS is configured to allow:
- **Origins**: localhost:3000 (frontend), localhost:3001 (BFF)
- **Methods**: GET, POST, PUT, DELETE, PATCH, OPTIONS
- **Headers**: All headers allowed
- **Credentials**: Enabled

## Running Locally

### Prerequisites

- Java 17+
- Docker (for backend services)

### Build

```bash
./gradlew :services:api-gateway:build
```

### Run

```bash
./gradlew :services:api-gateway:bootRun
```

Or with specific profile:

```bash
SPRING_PROFILES_ACTIVE=docker ./gradlew :services:api-gateway:bootRun
```

## Running with Docker Compose

The gateway is included in the main docker-compose.yml:

```bash
# Start all services including gateway
docker-compose up -d

# View gateway logs
docker-compose logs -f api-gateway

# Check gateway health
curl http://localhost:8000/actuator/health
```

## Configuration

### application.yml

Main configuration for local development:
- Port: 8000
- Keycloak URL: http://localhost:8080
- Service URLs: localhost with specific ports

### application-docker.yml

Docker environment configuration:
- Keycloak URL: http://keycloak:8080
- Service URLs: Docker service names

## Testing

### Health Check

```bash
curl http://localhost:8000/actuator/health
```

### Get Token and Test

```bash
# Get token for alice
TOKEN=$(./scripts/get-token.sh alice.employee)

# Test travel service via gateway
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8000/api/travel/bookings

# Test expense service via gateway
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8000/api/expenses/expenses
```

### View Routes

```bash
# See all configured routes
curl http://localhost:8000/actuator/gateway/routes | jq
```

### Circuit Breaker Status

```bash
# Check circuit breaker health
curl http://localhost:8000/actuator/health | jq '.components.circuitBreakers'
```

## Endpoints

### Management Endpoints

- **Health**: `GET /actuator/health`
- **Info**: `GET /actuator/info`
- **Metrics**: `GET /actuator/metrics`
- **Gateway Routes**: `GET /actuator/gateway/routes`
- **Swagger UI**: `GET /swagger-ui.html`
- **API Docs**: `GET /api-docs`

### Fallback Endpoints

- `/fallback/travel` - Travel service fallback
- `/fallback/expenses` - Expense service fallback
- `/fallback/approvals` - Approval service fallback
- `/fallback/delegations` - Delegation service fallback
- `/fallback/consent` - Consent service fallback

## Security

### JWT Token Requirements

The gateway validates JWT tokens from Keycloak with the following:

- **Issuer**: `http://keycloak:8080/realms/corporate-travel`
- **Algorithm**: RS256
- **Required Claims**: `sub`, `tenant_id`

### Role Extraction

Roles are extracted from:
- `realm_access.roles` - Realm-level roles
- `resource_access.{client}.roles` - Client-specific roles

Roles are prefixed with `ROLE_` in Spring Security context.

## Monitoring

### Logs

```bash
# View gateway logs
docker-compose logs -f api-gateway

# Search for specific requests
docker-compose logs api-gateway | grep "GET /api/travel"
```

### Metrics

Gateway exposes Prometheus metrics at `/actuator/prometheus`:

- Request count by route
- Circuit breaker state
- Response times
- Error rates

## Troubleshooting

### Gateway not starting

Check Keycloak is healthy:
```bash
docker-compose ps keycloak
curl http://localhost:8080/realms/corporate-travel
```

### 401 Unauthorized

- Verify token is valid: Check expiration time
- Verify issuer matches configuration
- Check Keycloak public key is accessible

### 503 Service Unavailable

- Check backend service is running
- Review circuit breaker status
- Check service URLs in configuration

### CORS errors

- Verify frontend origin is in allowed origins
- Check CORS configuration in application.yml
- Ensure credentials are enabled if needed

## Architecture Decisions

See ADR-017 for detailed rationale on choosing Spring Cloud Gateway over Kong or NGINX.

Key factors:
- Consistency with Spring Boot ecosystem
- Reusable security components
- Developer productivity
- Right-sized for corporate portal needs

## Future Enhancements

1. **Rate Limiting**: Add Redis-based rate limiting per user/tenant
2. **API Versioning**: Support multiple API versions
3. **Request Transformation**: Add/remove headers based on context
4. **Response Caching**: Cache frequently accessed resources
5. **Distributed Tracing**: Add OpenTelemetry integration
6. **API Documentation**: Aggregate OpenAPI specs from all services

## Related Documentation

- [ADR-017: Adopt API Gateway Pattern](../../architecture-decision-records/ADR-017:%20Adopt%20API%20Gateway%20Pattern.md)
- [ADR-018: Backend-for-Frontend Strategy](../../architecture-decision-records/ADR-018:%20Backend-for-Frontend%20(BFF)%20Strategy.md)
- [Travel Service Documentation](../travel-service/README.md)
- [Expense Service Documentation](../expense-service/README.md)
