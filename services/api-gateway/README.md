# API Gateway Service

Spring Cloud Gateway providing centralized routing, JWT validation, and traffic management for the Corporate Travel & Expense Platform.

**Port**: 8000  
**Spring Boot**: 3.2.2 / Java 17

## Overview

The API Gateway is the single entry point for external client requests. It:

- Validates JWT tokens once at the gateway level
- Routes requests to backend microservices by path
- Applies circuit breakers (Resilience4j)
- Injects security headers on all responses
- Manages CORS centrally

> The Employee BFF (port 8085) calls downstream services directly — it does **not** route through the gateway. The gateway is intended for direct API clients.

## Architecture

```
External Client (curl / frontend)
    ↓ HTTP with JWT
API Gateway (Port 8000)
    ├─ JWT Validation (Spring Security OAuth2)
    ├─ Route Matching
    ├─ Filters (Logging, Security Headers)
    └─ Circuit Breakers (Resilience4j)
    ↓
Backend Services
    ├─ Travel Service   (Port 8081)  ← /api/bookings/**
    └─ Expense Service  (Port 8082)  ← /api/expenses/**
```

## Routes

| Path | Backend Service | Port |
|------|----------------|------|
| `/api/bookings/**` | travel-service | 8081 |
| `/api/expenses/**` | expense-service | 8082 |

Routes to delegation-service (8083), consent-service (8084), and employee-bff (8085) are not yet wired through the gateway — access those services directly for now.

## Features

### JWT Authentication

All requests (except `/actuator/health`) require a valid JWT from Keycloak:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8000/api/bookings
```

### Circuit Breakers

Each backend has a dedicated circuit breaker:

- Sliding window: 10 requests
- Failure threshold: 50%
- Wait duration: 10 seconds

Fallback endpoints: `/fallback/travel`, `/fallback/expenses`

### Security Headers

All responses include:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Strict-Transport-Security: max-age=31536000`

### CORS

Configured for:
- Origins: `localhost:3000` (future frontend), `localhost:8085` (BFF)
- Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
- Credentials: enabled

## Running

```bash
# Build
./gradlew :services:api-gateway:build

# Run locally
./gradlew :services:api-gateway:bootRun

# Docker Compose
docker-compose up -d
docker-compose logs -f api-gateway
```

## Configuration

Routes are defined programmatically in `GatewayConfig.java`. The YAML configures JWT validation and server settings:

```yaml
# application-docker.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/corporate-travel
server:
  port: 8000
```

## Testing

```bash
# Health check
curl http://localhost:8000/actuator/health

# List configured routes
curl http://localhost:8000/actuator/gateway/routes | jq

# Get token and test routing
TOKEN=$(curl -s -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d "client_id=employee-portal&username=alice.employee&password=password123&grant_type=password" \
  | jq -r '.access_token')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8000/api/bookings
curl -H "Authorization: Bearer $TOKEN" http://localhost:8000/api/expenses
```

## Endpoints

### Management
- `GET /actuator/health` — health status
- `GET /actuator/metrics` — metrics
- `GET /actuator/gateway/routes` — configured routes
- `GET /swagger-ui.html` — Swagger UI
- `GET /api-docs` — OpenAPI spec

## Troubleshooting

**401 Unauthorized** — verify token is valid and not expired; check issuer matches `http://keycloak:8080/realms/corporate-travel`

**503 Service Unavailable** — check that the backend service is running (`docker-compose ps`); review circuit breaker status at `/actuator/health`

**Gateway won't start** — Keycloak must be healthy first: `curl http://localhost:8080/realms/corporate-travel`

## Architecture Decisions

- [ADR-017: API Gateway Pattern](../../architecture-decision-records/ADR-017:%20Adopt%20API%20Gateway%20Pattern.md)
- [ADR-018: BFF Strategy](../../architecture-decision-records/ADR-018:%20Backend-for-Frontend%20(BFF)%20Strategy.md)
