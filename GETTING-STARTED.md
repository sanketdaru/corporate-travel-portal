# Getting Started with Corporate Travel Platform

This quick start guide will help you get the platform up and running in minutes.

## ⚡ Quick Start

### Step 1: Prerequisites Check

Ensure you have the following installed:

```bash
# Podman with the compose subcommand (Docker with `docker compose` also works)
podman --version
podman compose version

# Tools used by the scripts
curl --version
jq --version

# Node.js 22+ (only for the frontend)
node --version
```

Java is optional. The Gradle build uses a Java 25 toolchain and downloads it automatically if no local JDK 25 is found. The container images build inside `gradle:9.8-jdk25-noble`.

> The commands below use `podman compose`. With Docker, replace it with `docker compose`.

### Step 2: Configure and Start

```bash
cd corporate-travel-portal

# Demo secrets (Keycloak admin, client secrets, demo user password, BFF grant encryption key)
cp .env.example .env

# Build and start everything
podman compose up -d --build
```

Compose starts the stack in this order:

1. `postgres`, `neo4j`, `opa`, `keycloak` (Keycloak 26.8 with the `token-exchange-delegation` and `parameterized-scopes` preview features)
2. `keycloak-config` (one-shot): applies the realm definition `infrastructure/keycloak/config/corporate-travel.json`
3. `keycloak-fgap` (one-shot): applies the FGAP v2 delegation permissions
4. The six Spring Boot services

**Expected time**: about 5 minutes on the first build, under a minute afterwards.

> `./scripts/setup-local.sh` starts only the infrastructure containers. It waits for the `corporate-travel` realm, which exists only after `keycloak-config` has run, so on a fresh volume use `podman compose up -d` instead.

### Step 3: Verify Infrastructure

```bash
podman compose ps
# Long-running services show "healthy"; keycloak-config and keycloak-fgap show "Exited (0)"
```

| Service | URL | Credentials |
|---------|-----|-------------|
| Keycloak Admin | http://localhost:8080/admin | admin / admin123 |
| Neo4j Browser | http://localhost:7474 | neo4j / password123 |
| OPA Health | http://localhost:8181/health | N/A |
| API Gateway | http://localhost:8000 | Bearer token |
| Services | travel 8081, expense 8082, delegation 8083, consent 8084, BFF 8085 | Bearer token |

### Step 4: Test Authentication

```bash
# Get a token for Alice (employee); uses the employee-bff client's password grant
./scripts/get-token.sh alice.employee password123

# You should see: ✅ Access Token obtained successfully!
```

### Step 5: Seed Demo Data and Run the Regression Test

```bash
# Bookings and expenses for all users, plus the Carol → Dave delegation
./scripts/seed-data.sh

# End-to-end delegation regression (Keycloak grant, delegated booking, audit, revocation)
./scripts/end-to-end-test/run-delegation-flow.sh
```

`seed-data.sh` also performs Carol's Keycloak authorization of the delegation (see [Testing delegation](#4-test-delegation-dave-for-carol)), so Dave can activate it straight away.

### Step 6: Test Authorization Policies

```bash
./scripts/test-opa-policy.sh

# You should see: ✅ PASS for all tests
```

### Step 7: Start the Frontend (optional)

```bash
cd frontend/nextjs
cp .env.example .env.local
npm install
npm run dev
# http://localhost:3000 — sign in as any demo user
```

## 🎯 What's Next?

### Option A: Explore the Architecture

1. **README.md** - System overview and architecture
2. **IMPLEMENTATION.md** - Implementation patterns and guides
3. **docs/** - Authentication, authorization and user journey guides
4. **architecture-decision-records/** - Detailed ADRs (delegation: ADR-024)

### Option B: Change or Add a Service

Follow the patterns and the "Building a New Service" checklist in IMPLEMENTATION.md.

### Option C: Test the Identity Flows

**1. Get tokens for different users:**

```bash
./scripts/get-token.sh alice.employee     # Employee
./scripts/get-token.sh bob.manager        # Manager
./scripts/get-token.sh carol.executive    # Executive
./scripts/get-token.sh dave.assistant     # Assistant
```

**2. Examine token claims:**

The script decodes the JWT and saves it to `/tmp/keycloak-token.txt`. Claims include:
- `sub`, `preferred_username`, `email`, `name`
- `tenant_id`, `employee_id`
- `realm_access.roles`
- `aud`: the four backend services and `employee-bff`

**3. Test OPA authorization:**

Modify `scripts/test-opa-policy.sh` to test your own scenarios.

#### 4. Test delegation (Dave for Carol)

Delegation uses Keycloak's RFC 8693 delegation (ADR-024), a preview feature in Keycloak 26.8:

1. Carol creates a delegation to Dave (Delegation page, or `POST /api/delegations`).
2. Carol authorizes it in Keycloak. The frontend sends her to Keycloak's consent screen; the API call is `POST /api/bff/delegation/{id}/grant`. The BFF stores her offline token encrypted.
3. Dave activates it: `POST /api/bff/delegation/activate/{id}`. This returns 409 until Carol has authorized it.
4. Dave's BFF calls now use tokens with `sub` = Carol and `act.sub` = Dave. Bookings are owned by Carol and record Dave as the creator.

`seed-data.sh` and `run-delegation-flow.sh` perform step 2 with `scripts/lib/delegation-grant.sh`.

## 🧪 Development Workflow

### Building Services

```bash
# Build and test all modules
./gradlew build

# Build a specific service
./gradlew :services:travel-service:build

# Run tests
./gradlew test
```

### Running Services Locally

```bash
# Option 1: Run with Gradle (infrastructure from compose)
./gradlew :services:travel-service:bootRun

# Option 2: Run in compose
podman compose up -d travel-service

# View logs
podman compose logs -f travel-service
```

### Making Changes

The images build from source, so rebuild the image rather than restarting the container:

```bash
# 1. Make code changes

# 2. Rebuild and restart the service
podman compose up -d --build travel-service

# 3. View logs
podman compose logs -f travel-service
```

## 📋 Common Commands

### Service Management

```bash
# Start everything
podman compose up -d

# Stop everything
podman compose down

# View logs for a specific service
podman compose logs -f [service-name]

# Restart a service
podman compose restart [service-name]

# Clean up everything
./scripts/cleanup.sh

# Clean up including data volumes (deletes all data)
./scripts/cleanup.sh --volumes
```

### Database Access

```bash
# Connect to PostgreSQL
podman compose exec postgres psql -U admin -d corporate_travel

# List schemas (travel, expense, delegation, consent, bff, keycloak)
\dn

# Query data
SELECT * FROM travel.bookings;
```

### Neo4j Access

```bash
# Open Neo4j Browser
open http://localhost:7474

# Or use Cypher from command line
podman compose exec neo4j cypher-shell -u neo4j -p password123

# Example query
MATCH (n) RETURN n LIMIT 10;
```

### Keycloak Management

The realm is defined as code in `infrastructure/keycloak/config/corporate-travel.json` and applied on every `up`. Changes made in the admin console are not kept in the file.

```bash
# Re-apply the realm and FGAP permissions after editing the definition
podman compose up keycloak-config keycloak-fgap

# Validate the realm definition against a throwaway Keycloak (same check as CI)
./scripts/keycloak-realm-test/validate-realm-config.sh

# Access admin console
open http://localhost:8080/admin
```

## 🔧 Troubleshooting

### Services Won't Start

**Problem**: Compose fails to start services

**Solutions**:
1. Check Podman is running: `podman ps` (on macOS: `podman machine start`)
2. Check ports aren't in use: `lsof -i :8000,8080,8081,8082,8083,8084,8085,5432,7474,7687,8181`
3. Try cleanup and restart:
   ```bash
   ./scripts/cleanup.sh --volumes
   podman compose up -d --build
   ```

### Keycloak Not Ready

**Problem**: Keycloak takes too long to start, or the `corporate-travel` realm is missing

**Solutions**:
1. Check logs: `podman compose logs keycloak keycloak-config keycloak-fgap`
2. Check the one-shots exited with 0: `podman compose ps -a`
3. Re-apply the realm: `podman compose up keycloak-config keycloak-fgap`

### Cannot Get Token

**Problem**: `./scripts/get-token.sh` fails

**Solutions**:
1. Verify Keycloak is running: `podman inspect --format '{{.State.Health.Status}}' corporate-travel-keycloak` (health endpoints are on the unpublished management port 9000), or `curl -f http://localhost:8080/realms/corporate-travel`
2. Check username/password are correct (demo password: `KC_DEMO_USER_PASSWORD`, default `password123`)
3. Check the `employee-bff` secret matches `KC_CLIENT_SECRET_EMPLOYEE_BFF` in `.env`

### Delegation Activation Returns 409

The delegator has not authorized the delegation in Keycloak yet. Sign in as Carol and choose "Authorize" on the Delegation page, or run `./scripts/seed-data.sh`.

### OPA Policies Fail

**Problem**: `./scripts/test-opa-policy.sh` shows failures

**Solutions**:
1. Check OPA is running: `curl http://localhost:8181/health`
2. Verify policies are loaded: `podman compose logs opa`
3. Test OPA directly:
   ```bash
   curl -X POST http://localhost:8181/v1/data/corporate/travel/authorization
   ```

### Database Connection Issues

**Problem**: Services can't connect to PostgreSQL

**Solutions**:
1. Verify PostgreSQL is healthy: `podman compose ps postgres`
2. Check connection:
   ```bash
   podman compose exec postgres pg_isready -U admin
   ```
3. Verify schemas exist:
   ```bash
   podman compose exec postgres psql -U admin -d corporate_travel -c "\dn"
   ```

## 💡 Tips and Best Practices

### Development Tips

1. **Use Local Profiles**: Create `application-local.yml` for local development settings
2. **Enable Debug Logging**: Set `logging.level.com.corporate.travel=DEBUG`
3. **Database Changes**: Add a Flyway migration under `src/main/resources/db/migration/`

### Security Tips

1. **Never commit credentials**: Use `.env` file and keep it in `.gitignore`
2. **Change default secrets**: The values in `.env.example` are demo values, including `BFF_GRANT_ENCRYPTION_KEY` (generate with `openssl rand -base64 32`)
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
A: Add the user to `infrastructure/keycloak/config/corporate-travel.json` and run `podman compose up keycloak-config`. The demo password is set only when a user is created.

**Q: How do I add a new service?**
A: Follow the pattern in IMPLEMENTATION.md, starting with the Travel Service example.

**Q: How do I test delegation?**
A: Use Dave (assistant) to act on behalf of Carol (executive) — see [Test delegation](#4-test-delegation-dave-for-carol) above and `docs/journeys/DELEGATE-JOURNEY.md`.

**Q: How do I modify authorization policies?**
A: Edit `infrastructure/opa/policies/authorization.rego`. OPA runs with `--watch` and reloads it automatically.

## ✨ You're Ready!

The stack is running with demo data. See IMPLEMENTATION.md for the patterns used across the services.

**Happy coding! 🚀**
