#!/bin/bash

# Corporate Travel Platform - Local Setup Script
# This script sets up the local development environment

set -e

echo "🚀 Setting up Corporate Travel Platform..."
echo ""

# Check prerequisites
echo "📋 Checking prerequisites..."
command -v docker >/dev/null 2>&1 || { echo "❌ Docker is required but not installed. Aborting." >&2; exit 1; }
command -v docker-compose >/dev/null 2>&1 || { echo "❌ Docker Compose is required but not installed. Aborting." >&2; exit 1; }
echo "✅ Docker and Docker Compose are installed"
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
docker-compose up -d postgres neo4j keycloak opa

echo ""
echo "⏳ Waiting for services to be healthy..."
echo "   This may take 30-60 seconds..."
echo ""

# Wait for PostgreSQL
echo "   Waiting for PostgreSQL..."
until docker-compose exec -T postgres pg_isready -U admin > /dev/null 2>&1; do
    printf "."
    sleep 2
done
echo " ✅ PostgreSQL is ready"

# Wait for Neo4j
echo "   Waiting for Neo4j..."
for i in {1..30}; do
    if curl -s http://localhost:7474 > /dev/null; then
        echo " ✅ Neo4j is ready"
        break
    fi
    printf "."
    sleep 2
done

# Wait for Keycloak
echo "   Waiting for Keycloak..."
for i in {1..60}; do
    if curl -s http://localhost:8080/health/ready > /dev/null 2>&1; then
        echo " ✅ Keycloak is ready"
        break
    fi
    printf "."
    sleep 2
done

# Wait for OPA
echo "   Waiting for OPA..."
for i in {1..15}; do
    if curl -s http://localhost:8181/health > /dev/null 2>&1; then
        echo " ✅ OPA is ready"
        break
    fi
    printf "."
    sleep 1
done

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
echo "   3. Start all services:   docker-compose up -d"
echo "   4. View logs:            docker-compose logs -f [service-name]"
echo "   5. Stop services:        docker-compose down"
echo ""

echo "👥 Test Users (password: password123):"
echo "   alice.employee   - Standard employee (Tenant A)"
echo "   bob.manager      - Manager with approval rights (Tenant A)"
echo "   carol.executive  - Executive for delegation (Tenant A)"
echo "   dave.assistant   - Executive assistant (Tenant A)"
echo "   eve.employee     - Employee in Tenant B"
echo ""

echo "✨ Setup complete! Happy coding! 🎉"
