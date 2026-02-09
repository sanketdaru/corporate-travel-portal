# Corporate Travel & Expense Platform - Identity Reference Implementation

A comprehensive multi-tenant Corporate Travel & Expense platform demonstrating advanced identity and access management patterns using Keycloak, OAuth 2.0 Token Exchange, Open Policy Agent, and microservices architecture.

## 🎯 Overview

This project implements a reference architecture for enterprise identity management featuring:

- **Federated Identity** with Keycloak as central IAM
- **Delegated Identity** using OAuth 2.0 Token Exchange (RFC 8693)
- **Multi-Tenant Isolation** with single realm strategy
- **Fine-Grained Authorization** with Open Policy Agent (OPA)
- **Backend-for-Frontend (BFF)** pattern
- **Microservices Architecture** with Spring Boot
- **Comprehensive Audit Trail** for compliance

## 📋 Table of Contents

- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Services](#services)
- [Demo Scenarios](#demo-scenarios)
- [Development](#development)
- [Testing](#testing)
- [Architecture Decision Records](#architecture-decision-records)

## 🏗️ Architecture

### System Architecture

```
┌─────────────────┐
│  Employee Portal│ (Next.js + React)
└────────┬────────┘
         │
    ┌────▼─────┐
    │ Employee │
    │   BFF    │ (Spring Boot)
    └────┬─────┘
         │
    ┌────▼─────────────────────────────────┐
    │         API Gateway                   │ (Spring Cloud Gateway)
    │    (JWT Validation, Routing)          │
    └────┬─────────────────────────────────┘
         │
    ┌────┴──────┬──────────┬──────────┬────────────┐
    │           │          │          │            │
┌───▼───┐  ┌───▼───┐  ┌───▼────┐ ┌──▼──────┐ ┌──▼────────┐
│Travel │  │Expense│  │Approval│ │Consent  │ │Delegation │
│Service│  │Service│  │Service │ │Service  │ │Service    │
└───┬───┘  └───┬───┘  └───┬────┘ └──┬──────┘ └──┬────────┘
    │          │          │         │            │
    └──────────┴──────────┴─────────┴────────────┘
                     │                    │
              ┌──────▼──────┐      ┌─────▼─────┐
              │  PostgreSQL │      │   Neo4j   │
              └─────────────┘      └───────────┘

         ┌──────────┐          ┌──────────┐
         │ Keycloak │          │   OPA    │
         │   IAM    │          │  Policy  │
         └──────────┘          └──────────┘
```

### Key Identity Patterns

1. **Authentication**: Keycloak handles user authentication with SSO support
2. **Token Exchange**: Delegated actions use OAuth 2.0 Token Exchange (RFC 8693)
3. **Authorization**: OPA evaluates fine-grained policies based on context
4. **Multi-Tenancy**: Single realm with group-based tenant isolation
5. **Consent Management**: External service for purpose-bound delegation
6. **Audit Trail**: Actor/Subject tracking for all identity-sensitive operations

## 📦 Prerequisites

- **Docker** 24.0+ and Docker Compose
- **Java** 17+ (for local development)
- **Node.js** 18+ (for frontend development)
- **Gradle** 8.0+ (wrapper included)
- **Git**

### System Resources

- Minimum 8GB RAM
- 20GB available disk space

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd corporate-travel-portal
```

### 2. Start Infrastructure Services

```bash
# Start Keycloak, PostgreSQL, Neo4j, OPA
docker-compose up -d postgres neo4j keycloak opa

# Wait for services to be healthy (30-60 seconds)
docker-compose ps
```

### 3. Verify Infrastructure

- **Keycloak**: http://localhost:8080 (admin/admin123)
- **Neo4j Browser**: http://localhost:7474 (neo4j/password123)
- **OPA**: http://localhost:8181/health
- **PostgreSQL**: localhost:5432 (admin/admin123)

### 4. Build Services

```bash
# Build all Spring Boot services
./gradlew build

# Or build individual service
./gradlew :services:travel-service:build
```

### 5. Start Application Services

```bash
# Start all application services
docker-compose up -d

# Or start services individually for development
./gradlew :services:travel-service:bootRun
```

### 6. Access the Application

- **Employee Portal**: http://localhost:3000
- **API Gateway**: http://localhost:8000
- **BFF**: http://localhost:3001

## 📁 Project Structure

```
corporate-travel-portal/
├── architecture-decision-records/   # ADRs documenting architectural choices
├── infrastructure/
│   ├── databases/                   # Database init scripts
│   ├── keycloak/                    # Keycloak realm configuration
│   └── opa/                         # OPA authorization policies
├── services/
│   ├── shared/                      # Shared libraries
│   │   ├── security-commons/       # Security utilities and OPA client
│   │   ├── domain-models/          # Shared domain models
│   │   └── observability/          # OpenTelemetry setup
│   ├── api-gateway/                # Spring Cloud Gateway
│   ├── travel-service/             # Travel booking domain service
│   ├── expense-service/            # Expense management domain service
│   ├── approval-service/           # Approval workflow service
│   ├── consent-service/            # Consent and purpose binding
│   ├── delegation-service/         # Delegation relationship management
│   └── employee-bff/               # Backend-for-Frontend
├── frontend/
│   └── employee-portal/            # Next.js + React application
├── docs/                           # Additional documentation
├── scripts/                        # Utility scripts
├── docker-compose.yml              # Docker Compose configuration
├── build.gradle                    # Root Gradle build
└── settings.gradle                 # Gradle multi-project settings
```

## 🔧 Services

### Core Infrastructure

#### Keycloak (IAM Platform)
- **Port**: 8080
- **Realm**: corporate-travel
- **Users**: alice.employee, bob.manager, carol.executive, dave.assistant, eve.employee
- **Default Password**: password123

#### PostgreSQL (Primary Database)
- **Port**: 5432
- **Schemas**: travel, expense, approval, consent, delegation, keycloak

#### Neo4j (Graph Database)
- **HTTP**: 7474
- **Bolt**: 7687
- **Purpose**: Delegation relationship graph

#### OPA (Policy Engine)
- **Port**: 8181
- **Policies**: Multi-tenant isolation, delegation-aware authorization

### Application Services

#### API Gateway
- **Port**: 8000
- **Responsibilities**: JWT validation, routing, rate limiting

#### Travel Service
- **Domain**: Travel booking management
- **Features**: Create bookings, delegated booking, approval workflow
- **Database**: travel schema

#### Expense Service
- **Domain**: Expense submission and tracking
- **Features**: Submit expenses, link to bookings, approval workflow
- **Database**: expense schema

#### Approval Service
- **Domain**: Multi-step approval workflows
- **Features**: Workflow state machine, delegation support, audit trail
- **Database**: approval schema

#### Consent Service
- **Domain**: Consent and purpose binding
- **Features**: Grant/revoke consent, purpose-based access, consent validation
- **Database**: consent schema

#### Delegation Service
- **Domain**: Delegation relationship management
- **Features**: Create/revoke delegations, relationship traversal, temporal delegation
- **Databases**: delegation schema (PostgreSQL) + Neo4j graph

#### Employee BFF
- **Port**: 3001
- **Responsibilities**: Token exchange, API aggregation, session management

### Frontend

#### Employee Portal
- **Port**: 3000
- **Framework**: Next.js 14 + React 18 + TypeScript
- **Design**: shadcn/ui + Tailwind CSS
- **Auth**: NextAuth.js with Keycloak provider

## 🎬 Demo Scenarios

### Scenario 1: Basic Employee Flow

1. **Login** as Alice (alice.employee / password123)
2. **Create Booking**: Book a flight to NYC
3. **Submit Expense**: Create expense linked to booking
4. **Login** as Bob (bob.manager / password123)
5. **Approve**: Bob approves Alice's expense

### Scenario 2: Delegation Flow

1. **Login** as Carol (carol.executive / password123)
2. **Create Delegation**: Grant booking permission to Dave
3. **Login** as Dave (dave.assistant / password123)
4. **Act as Carol**: Switch to "Acting as Carol" mode
5. **Book Travel**: Dave books hotel for Carol
6. **Audit**: System shows Actor=Dave, Subject=Carol

### Scenario 3: Multi-Tenant Isolation

1. **Login** as Alice (Tenant A)
2. **Attempt Access**: Try to view Tenant B data
3. **Denied**: OPA policy blocks cross-tenant access
4. **Audit**: Attempt is logged

## 💻 Development

### Build Commands

```bash
# Build all services
./gradlew build

# Build specific service
./gradlew :services:travel-service:build

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```

### Running Services Locally

```bash
# Start infrastructure only
docker-compose up -d postgres neo4j keycloak opa

# Run service with Spring Boot
./gradlew :services:travel-service:bootRun

# Run with active profile
./gradlew :services:travel-service:bootRun --args='--spring.profiles.active=local'
```

### Frontend Development

```bash
cd frontend/employee-portal

# Install dependencies
npm install

# Run development server
npm run dev

# Build for production
npm run build
```

### Database Migrations

Database schemas are automatically created via init scripts. For changes:

1. Update SQL scripts in `infrastructure/databases/init-scripts/`
2. Restart PostgreSQL: `docker-compose restart postgres`

## 🧪 Testing

### Test Users

| Username | Password | Role | Tenant | Purpose |
|----------|----------|------|--------|---------|
| alice.employee | password123 | employee | tenant-a | Standard employee |
| bob.manager | password123 | manager | tenant-a | Manager with approval rights |
| carol.executive | password123 | executive | tenant-a | Executive for delegation demos |
| dave.assistant | password123 | assistant | tenant-a | Assistant to executive |
| eve.employee | password123 | employee | tenant-b | Tenant isolation demos |

### Testing Authorization

```bash
# Get access token
curl -X POST "http://localhost:8080/realms/corporate-travel/protocol/openid-connect/token" \
  -d "client_id=employee-portal" \
  -d "username=alice.employee" \
  -d "password=password123" \
  -d "grant_type=password"

# Use token to call API
curl -H "Authorization: Bearer <token>" \
  http://localhost:8000/api/bookings
```

### Testing OPA Policies

```bash
# Test policy directly
curl -X POST http://localhost:8181/v1/data/corporate/travel/authorization/allow \
  -H "Content-Type: application/json" \
  -d @test-input.json
```

## 📚 Architecture Decision Records

All architectural decisions are documented in `/architecture-decision-records/`:

- **ADR-001**: Corporate Travel & Expense as Reference Domain
- **ADR-002**: Keycloak as Central IAM Platform
- **ADR-003**: Multi-Tenant Identity Model
- **ADR-004**: OAuth 2.0 Token Exchange for Delegation
- **ADR-005**: External Consent and Purpose Binding
- **ADR-006**: Microservices with BFF Pattern
- **ADR-007**: External Policy Engine for Authorization
- **ADR-019**: Open Policy Agent Implementation
- [See all ADRs](./architecture-decision-records/)

## 🔐 Security Considerations

### Production Checklist

- [ ] Change all default passwords
- [ ] Use HTTPS/TLS for all services
- [ ] Configure proper CORS policies
- [ ] Enable Keycloak security features (brute force protection)
- [ ] Implement rate limiting
- [ ] Use HashiCorp Vault for secrets (see ADR-021)
- [ ] Enable audit logging
- [ ] Configure proper firewall rules
- [ ] Use workload identity for service-to-service auth

## 🤝 Contributing

1. Review Architecture Decision Records
2. Follow existing code patterns
3. Write tests for new features
4. Update documentation
5. Submit pull request

## 📄 License

[Your License Here]

## 🙏 Acknowledgments

This implementation demonstrates patterns from:
- OAuth 2.0 Token Exchange (RFC 8693)
- Open Policy Agent best practices
- Spring Security OAuth2 patterns
- Keycloak identity brokering

## 📞 Support

For issues and questions:
- GitHub Issues: [Project Issues]
- Documentation: `/docs/`
- ADRs: `/architecture-decision-records/`
