#!/bin/bash

# Corporate Travel Platform - Local Setup Script
# This script sets up the local development environment

set -e

echo "🚀 Setting up Corporate Travel Platform..."
echo ""

# Check prerequisites
echo "📋 Checking prerequisites..."
if command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
    COMPOSE="podman compose"
elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
else
    echo "❌ Podman or Docker with the compose subcommand is required. Aborting." >&2
    exit 1
fi
echo "✅ Using: $COMPOSE"
echo ""

# Create .env file if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env file from template..."
    cp .env.example .env
    echo "✅ .env file created"
else
    echo "✅ .env file already exists"
fi
echo ""

# Start infrastructure services
echo "🏗️  Starting infrastructure services (Postgres, Neo4j, Keycloak, OPA)..."
$COMPOSE up -d postgres neo4j keycloak opa

echo ""
echo "⏳ Waiting for services to be healthy..."
echo "   This may take 30-60 seconds..."
echo ""

# wait_for <name> <attempts> <sleep-seconds> <url>
# Fails the script if the URL does not return a 2xx response in time.
wait_for() {
    local name="$1" attempts="$2" pause="$3" url="$4"
    echo "   Waiting for $name..."
    for ((i = 1; i <= attempts; i++)); do
        if curl -sf "$url" > /dev/null 2>&1; then
            echo " ✅ $name is ready"
            return 0
        fi
        printf "."
        sleep "$pause"
    done
    echo ""
    echo " ❌ $name did not become ready at $url" >&2
    exit 1
}

# Wait for PostgreSQL
echo "   Waiting for PostgreSQL..."
until $COMPOSE exec -T postgres pg_isready -U admin > /dev/null 2>&1; do
    printf "."
    sleep 2
done
echo " ✅ PostgreSQL is ready"

wait_for "Neo4j" 30 2 http://localhost:7474
# Health endpoints live on the unpublished management port 9000, so probe the
# realm endpoint instead (same check as the compose healthcheck).
wait_for "Keycloak" 60 2 http://localhost:8080/realms/corporate-travel
wait_for "OPA" 15 1 http://localhost:8181/health

echo ""
echo "✅ All infrastructure services are running!"
echo ""

# Display service URLs
echo "🌐 Service URLs:"
echo "   Keycloak Admin:  http://localhost:8080/admin (admin/admin123)"
echo "   Keycloak Realm:  http://localhost:8080/realms/corporate-travel"
echo "   Neo4j Browser:   http://localhost:7474 (neo4j/password123)"
echo "   OPA:             http://localhost:8181/health"
echo "   PostgreSQL:      localhost:5432 (admin/admin123)"
echo ""

echo "📚 Next Steps:"
echo "   1. Build services:       ./gradlew build"
echo "   2. Run a service:        ./gradlew :services:travel-service:bootRun"
echo "   3. Start all services:   $COMPOSE up -d"
echo "   4. View logs:            $COMPOSE logs -f [service-name]"
echo "   5. Stop services:        $COMPOSE down"
echo ""

echo "👥 Test Users (password: password123):"
echo "   alice.employee   - Standard employee (Tenant A)"
echo "   bob.manager      - Manager with approval rights (Tenant A)"
echo "   carol.executive  - Executive for delegation (Tenant A)"
echo "   dave.assistant   - Executive assistant (Tenant A)"
echo "   eve.employee     - Employee in Tenant B"
echo ""

echo "✨ Setup complete! Happy coding! 🎉"
