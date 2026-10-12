# API Gateway Service

Spring Cloud Gateway providing centralized routing and JWT validation for the Corporate Travel & Expense Platform.

**Port**: 8000  
**Spring Boot**: 4.1.1 / Java 25 (Spring Cloud 2025.1.3, Gateway 5.0, WebFlux)

## Overview

The API Gateway is the single entry point for external client requests. It:

- Validates JWT signature, issuer and audience at the gateway level
- Routes requests to backend microservices by path
- Injects security headers on all responses
- Manages CORS centrally

> The Next.js frontend calls the gateway (via its `/api/bookings`, `/api/expenses`, `/api/delegations`, `/api/consents` rewrites) with the `employee-portal` token. The Employee BFF (port 8085) calls downstream services directly and does **not** route through the gateway.

## Architecture

```
External Client (curl / frontend)
    ↓ HTTP with JWT
API Gateway (Port 8000)
    ├─ JWT Validation (Spring Security OAuth2, audience check)
    ├─ Route Matching
    └─ Filters (Logging, Security Headers)
    ↓
Backend Services
    ├─ Travel Service     (Port 8081)  ← /api/bookings/**
    ├─ Expense Service    (Port 8082)  ← /api/expenses/**
    ├─ Delegation Service (Port 8083)  ← /api/delegations/**
    └─ Consent Service    (Port 8084)  ← /api/consents/**
```

## Routes

| Path | Backend Service | Port |
|------|----------------|------|
| `/api/bookings/**` | travel-service | 8081 |
| `/api/expenses/**` | expense-service | 8082 |
| `/api/delegations/**` | delegation-service | 8083 |
| `/api/consents/**` | consent-service | 8084 |
| `/travel-service-docs/**`, `/expense-service-docs/**` | OpenAPI specs of travel / expense service | 8081 / 8082 |

The employee-bff (8085) is not routed through the gateway.

## Features

### JWT Authentication

All requests except actuator and API-docs endpoints require a valid JWT from Keycloak whose `aud` contains at least one of `travel-service`, `expense-service`, `delegation-service`, `consent-service`. Each backend service then enforces its own audience.

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8000/api/bookings
```

### Fallbacks

`FallbackController` serves `/fallback/{travel,expenses,approvals,delegations,consent}`. No circuit breaker filters are wired to the routes yet.

### Security Headers

All responses include:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- `Referrer-Policy: strict-origin-when-cross-origin`

### CORS

Configured for:
- Origins: `GATEWAY_CORS_ALLOWED_ORIGINS`, comma-separated (default `http://localhost:3000`, the Next.js frontend). The frontend normally proxies through Next.js rewrites (same origin), so CORS matters only for direct browser calls.
- Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
- Credentials: enabled

## Running

```bash
# Build
./gradlew :services:api-gateway:build

# Run locally
./gradlew :services:api-gateway:bootRun

# Compose
podman compose up -d
podman compose logs -f api-gateway
```

## Configuration

Routes are defined programmatically in `GatewayConfig.java`. The YAML configures JWT validation and server settings:

```yaml
# application.yml + application-docker.yml (merged)
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/corporate-travel   # token iss
          jwk-set-uri: http://keycloak:8080/realms/corporate-travel/protocol/openid-connect/certs
          audiences: [travel-service, expense-service, delegation-service, consent-service]
server:
  port: 8000
```

## Testing

```bash
# Health check
curl http://localhost:8000/actuator/health

# List configured routes
curl http://localhost:8000/actuator/gateway/routes | jq

# Get token and test routing (employee-bff is the only client with direct access grants)
TOKEN=$(curl -s -X POST http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token \
  -d "client_id=employee-bff&client_secret=bff-service-secret-change-in-production" \
  -d "username=alice.employee&password=password123&grant_type=password" \
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

**401 Unauthorized** — verify the token is valid and not expired, its `iss` is `http://localhost:8080/realms/corporate-travel`, and its `aud` contains a backend service

**503 Service Unavailable** — check that the backend service is running (`podman compose ps`)

**Gateway won't start** — Keycloak must be healthy first: `curl http://localhost:8080/realms/corporate-travel`

## Architecture Decisions

- [ADR-017: API Gateway Pattern](../../architecture-decision-records/ADR-017:%20Adopt%20API%20Gateway%20Pattern.md)
- [ADR-018: BFF Strategy](../../architecture-decision-records/ADR-018:%20Backend-for-Frontend%20(BFF)%20Strategy.md)
