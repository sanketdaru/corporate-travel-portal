# Getting Started with Corporate Travel Platform

This quick start guide will help you get the platform up and running in minutes.

## ⚡ Quick Start (5 Minutes)

### Step 1: Prerequisites Check

Ensure you have the following installed:

```bash
# Check Docker
docker --version
# Should output: Docker version 24.0+ or higher

# Check Docker Compose
docker-compose --version
# Should output: Docker Compose version 2.0+ or higher

# Check Java (optional, for local development)
java -version
# Should output: Java 17 or higher
```

### Step 2: Clone and Setup

```bash
# Clone the repository (if not already done)
cd corporate-travel-portal

# Run the setup script
./scripts/setup-local.sh
```

The setup script will:
- ✅ Create environment configuration
- ✅ Start infrastructure services (PostgreSQL, Neo4j, Keycloak, OPA)
- ✅ Wait for all services to be healthy
- ✅ Display service URLs and test credentials

**Expected time**: 1-2 minutes

### Step 3: Verify Infrastructure

Once setup completes, verify services are running:

```bash
# Check service status
docker-compose ps

# All services should show "healthy" status
```

Access the admin consoles:

| Service | URL | Credentials |
|---------|-----|-------------|
| Keycloak Admin | http://localhost:8080/admin | admin / admin123 |
| Neo4j Browser | http://localhost:7474 | neo4j / password123 |
| OPA Health | http://localhost:8181/health | N/A |

### Step 4: Test Authentication

Get an access token to verify Keycloak is configured correctly:

```bash
# Get token for Alice (employee)
./scripts/get-token.sh alice.employee password123

# You should see: ✅ Access Token obtained successfully!
```

### Step 5: Test Authorization Policies

Verify OPA policies are working:

```bash
# Run policy tests
./scripts/test-opa-policy.sh

# You should see: ✅ PASS for all tests
```

## 🎯 What's Next?

### Option A: Explore the Architecture

Read through the documentation to understand the system:

1. **README.md** - System overview and architecture
2. **IMPLEMENTATION.md** - Implementation patterns and guides
3. **architecture-decision-records/** - Detailed ADRs

### Option B: Implement Services

Follow the implementation guide to build services:

```bash
# The foundation is complete. Start with:
# 1. Travel Service - See IMPLEMENTATION.md for step-by-step guide
# 2. Expense Service - Similar pattern to Travel Service
# 3. Other services as needed
```

### Option C: Test the Identity Flows

Experiment with the identity patterns:

**1. Get tokens for different users:**

```bash
# Employee
./scripts/get-token.sh alice.employee

# Manager
./scripts/get-token.sh bob.manager

# Executive
./scripts/get-token.sh carol.executive

# Assistant
./scripts/get-token.sh dave.assistant
```

**2. Examine token claims:**

The script will decode and display the JWT claims, showing:
- User ID and username
- Tenant ID
- Roles
- Custom attributes

**3. Test OPA authorization:**

Modify `scripts/test-opa-policy.sh` to test your own scenarios.

## 🧪 Development Workflow

### Building Services

```bash
# Build all projects (when services are implemented)
./gradlew build

# Build specific service
./gradlew :services:travel-service:build

# Run tests
./gradlew test
```

### Running Services Locally

```bash
# Option 1: Run with Gradle (for development)
./gradlew :services:travel-service:bootRun

# Option 2: Run with Docker Compose
docker-compose up -d travel-service

# View logs
docker-compose logs -f travel-service
```

### Making Changes

```bash
# 1. Make code changes

# 2. Rebuild
./gradlew :services:travel-service:build

# 3. Restart service
docker-compose restart travel-service

# 4. View logs
docker-compose logs -f travel-service
```

## 📋 Common Commands

### Service Management

```bash
# Start all infrastructure
./scripts/setup-local.sh

# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs for specific service
docker-compose logs -f [service-name]

# View logs for all services
docker-compose logs -f

# Restart a service
docker-compose restart [service-name]

# Clean up everything
./scripts/cleanup.sh

# Clean up including data volumes
./scripts/cleanup.sh --volumes
```

### Database Access

```bash
# Connect to PostgreSQL
docker-compose exec postgres psql -U admin -d corporate_travel

# List schemas
\dn

# Connect to specific schema
SET search_path TO travel;

# List tables
\dt

# Query data
SELECT * FROM travel.bookings;
```

### Neo4j Access

```bash
# Open Neo4j Browser
open http://localhost:7474

# Or use Cypher from command line
docker-compose exec neo4j cypher-shell -u neo4j -p password123

# Example query
MATCH (n) RETURN n LIMIT 10;
```

### Keycloak Management

```bash
# Access admin console
open http://localhost:8080/admin

# View realm configuration
open http://localhost:8080/realms/corporate-travel
```

## 🔧 Troubleshooting

### Services Won't Start

**Problem**: Docker Compose fails to start services

**Solutions**:
1. Check Docker is running: `docker ps`
2. Check ports aren't in use: `lsof -i :8080,5432,7474,7687,8181`
3. Try cleanup and restart:
   ```bash
   ./scripts/cleanup.sh --volumes
   ./scripts/setup-local.sh
   ```

### Keycloak Not Ready

**Problem**: Keycloak takes too long to start or realm not imported

**Solutions**:
1. Check Keycloak logs: `docker-compose logs keycloak`
2. Verify realm file exists: `ls -la infrastructure/keycloak/realm-export.json`
3. Restart Keycloak: `docker-compose restart keycloak`
4. Import realm manually via admin console

### Cannot Get Token

**Problem**: `./scripts/get-token.sh` fails

**Solutions**:
1. Verify Keycloak is running: `curl http://localhost:8080/health/ready`
2. Check username/password are correct
3. Verify realm is configured: `open http://localhost:8080/admin`

### OPA Policies Fail

**Problem**: `./scripts/test-opa-policy.sh` shows failures

**Solutions**:
1. Check OPA is running: `curl http://localhost:8181/health`
2. Verify policies are loaded: `docker-compose logs opa`
3. Test OPA directly:
   ```bash
   curl -X POST http://localhost:8181/v1/data/corporate/travel/authorization
   ```

### Database Connection Issues

**Problem**: Services can't connect to PostgreSQL

**Solutions**:
1. Verify PostgreSQL is healthy: `docker-compose ps postgres`
2. Check connection:
   ```bash
   docker-compose exec postgres pg_isready -U admin
   ```
3. Verify schemas exist:
   ```bash
   docker-compose exec postgres psql -U admin -d corporate_travel -c "\dn"
   ```

## 💡 Tips and Best Practices

### Development Tips

1. **Use Local Profiles**: Create `application-local.yml` for local development settings
2. **Enable Debug Logging**: Set `logging.level.com.corporate.travel=DEBUG`
3. **Hot Reload**: Use Spring DevTools for faster development cycles
4. **Database Changes**: Use Flyway or Liquibase for schema migrations

### Security Tips

1. **Never commit credentials**: Use `.env` file and keep it in `.gitignore`
2. **Change default passwords**: Update passwords for production environments
3. **Use HTTPS**: Enable TLS for all services in production
4. **Rotate secrets**: Implement secret rotation for service credentials

### Testing Tips

1. **Write integration tests**: Test with actual Keycloak and OPA
2. **Mock external services**: Use WireMock or similar for testing
3. **Test tenant isolation**: Ensure cross-tenant access is blocked
4. **Test delegation flows**: Verify actor/subject tracking works

## 📚 Additional Resources

- **README.md**: Complete system documentation
- **IMPLEMENTATION.md**: Service implementation guide
- **architecture-decision-records/**: Architectural decisions
- **Spring Security OAuth2**: https://spring.io/projects/spring-security-oauth
- **Keycloak Documentation**: https://www.keycloak.org/documentation
- **OPA Documentation**: https://www.openpolicyagent.org/docs/latest/

## 🤝 Getting Help

### Documentation

- Start with README.md for architecture overview
- Check IMPLEMENTATION.md for implementation patterns
- Review ADRs for design decisions

### Common Questions

**Q: How do I add a new user?**
A: Edit `infrastructure/keycloak/realm-export.json` and restart Keycloak, or add via admin console.

**Q: How do I add a new service?**
A: Follow the pattern in IMPLEMENTATION.md, starting with the Travel Service example.

**Q: How do I test delegation?**
A: Use Dave (assistant) to act on behalf of Carol (executive) - see demo scenarios in README.md.

**Q: How do I modify authorization policies?**
A: Edit `infrastructure/opa/policies/authorization.rego` and restart OPA.

## ✨ You're Ready!

The foundation is complete and ready for development. Start building services following the patterns in IMPLEMENTATION.md.

**Happy coding! 🚀**
