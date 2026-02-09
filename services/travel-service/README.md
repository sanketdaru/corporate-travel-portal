# Travel Service

Travel Service manages travel bookings (flights, hotels, car rentals) with multi-tenant support and OPA-based authorization.

## Features

- ✅ CRUD operations for travel bookings
- ✅ Multi-tenant data isolation
- ✅ OPA authorization integration
- ✅ JWT-based authentication
- ✅ Delegation-aware operations
- ✅ RESTful API with RFC 7807 error handling
- ✅ Spring Boot Actuator health checks

## API Endpoints

### Create Booking
```bash
POST /api/bookings
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN>

{
  "bookingType": "FLIGHT",
  "destination": "New York",
  "startDate": "2024-06-01",
  "endDate": "2024-06-05",
  "totalAmount": 500.00
}
```

### List User Bookings
```bash
GET /api/bookings
Authorization: Bearer <JWT_TOKEN>
```

### Get Specific Booking
```bash
GET /api/bookings/{id}
Authorization: Bearer <JWT_TOKEN>
```

### Update Booking Status
```bash
PUT /api/bookings/{id}/status
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN>

{
  "status": "CONFIRMED"
}
```

### Delete Booking
```bash
DELETE /api/bookings/{id}
Authorization: Bearer <JWT_TOKEN>
```

## Building

```bash
# From project root
./gradlew :services:travel-service:build

# Skip tests
./gradlew :services:travel-service:build -x test
```

## Running Locally

```bash
# Start infrastructure first
./scripts/setup-local.sh

# Run the service
./gradlew :services:travel-service:bootRun

# Service will be available at http://localhost:8081
```

## Running with Docker Compose

```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f travel-service

# Stop services
docker-compose down
```

## Configuration

### Environment Variables
- `SPRING_PROFILES_ACTIVE`: Set to `docker` for Docker environment
- `SPRING_DATASOURCE_URL`: PostgreSQL connection URL
- `KEYCLOAK_URL`: Keycloak server URL
- `OPA_URL`: OPA server URL

### Database Schema
The service uses the `travel` schema in PostgreSQL with the following tables:
- `bookings`: Main booking entity
- `booking_audit`: Audit trail (not yet implemented)

## Security

- **Authentication**: JWT tokens from Keycloak
- **Authorization**: OPA policies evaluate:
  - Multi-tenant isolation (tenant_id check)
  - Role-based access control
  - Delegation permissions
  - Resource ownership

## Health Check

```bash
curl http://localhost:8081/actuator/health
```

## Testing

```bash
# Get a JWT token
./scripts/get-token.sh alice.employee

# Create a booking
curl -X POST http://localhost:8081/api/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bookingType": "FLIGHT",
    "destination": "New York",
    "startDate": "2024-06-01",
    "endDate": "2024-06-05",
    "totalAmount": 500.00
  }'

# List bookings
curl http://localhost:8081/api/bookings \
  -H "Authorization: Bearer $TOKEN"
```

## Architecture

```
BookingController (REST API)
    ↓
BookingService (Business Logic + OPA Authorization)
    ↓
BookingRepository (Spring Data JPA)
    ↓
PostgreSQL (travel schema)
```

## Dependencies

- Spring Boot 3.2.2
- Spring Security OAuth2 Resource Server
- Spring Data JPA
- PostgreSQL Driver
- Shared security-commons library
- Shared domain-models library

## API Documentation

The Travel Service provides comprehensive API documentation using OpenAPI 3.0 (Swagger).

### Swagger UI

Access the interactive API documentation at:
```
http://localhost:8081/swagger-ui.html
```

Features:
- Interactive API exploration
- Request/response examples
- JWT authentication support
- Try-it-out functionality for each endpoint

### OpenAPI Specification

Access the raw OpenAPI specification (JSON format):
```
http://localhost:8081/api-docs
```

### Using Swagger UI with Authentication

1. Start infrastructure: `./scripts/setup-local.sh`
2. Get a JWT token: `./scripts/get-token.sh alice.employee`
3. Copy the token value from the script output
4. In Swagger UI, click the "Authorize" button (lock icon at top right)
5. Enter: `Bearer <your-token>` (replace `<your-token>` with actual token)
6. Click "Authorize" then "Close"
7. Now you can execute API requests directly from Swagger UI

## Future Enhancements

- [ ] Implement audit logging (BookingAudit entity)
- [ ] Add integration tests
- [ ] Add unit tests
- [ ] Implement booking approval workflow integration
- [ ] Add search and filter capabilities
- [ ] Implement pagination
