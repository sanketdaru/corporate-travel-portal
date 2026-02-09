# Product Context

## Why This Exists

This platform serves as a **reference implementation** for enterprise identity management patterns. It addresses the gap between theoretical identity concepts and practical implementation by demonstrating advanced patterns in a realistic business domain.

### Problems Solved

1. **Identity Pattern Education**
   - Shows how OAuth 2.0 Token Exchange actually works in practice
   - Demonstrates proper multi-tenant isolation strategies
   - Illustrates consent-based delegation with audit trails

2. **Architecture Validation**
   - Proves that complex identity patterns can work together cohesively
   - Tests OPA integration with Spring Security
   - Validates Keycloak as enterprise IAM platform

3. **Compliance Requirements**
   - Demonstrates actor/subject tracking for regulatory audit
   - Shows purpose-bound access control
   - Provides immutable audit ledger patterns

## How It Should Work

### Core User Flows

#### Flow 1: Basic Employee Journey
```
Alice (Employee) → Login → Book Travel → Submit Expense → Bob (Manager) Approves
```
- Single-tenant operations
- Standard RBAC authorization
- Basic audit logging

#### Flow 2: Delegation Journey
```
Carol (Executive) → Grant Delegation to Dave (Assistant)
Dave → Switch to "Act as Carol" → Book Travel for Carol
System → Records Actor=Dave, Subject=Carol
```
- OAuth 2.0 Token Exchange
- Consent validation
- Delegation audit trail

#### Flow 3: Multi-Tenant Isolation
```
Alice (Tenant A) → Attempts Access to → Eve's Data (Tenant B)
OPA → Denies Access
System → Logs Attempt
```
- Tenant boundary enforcement
- Policy-based denial
- Security event logging

### User Experience Goals

#### For Employees
- **Simple**: Login with SSO, book travel, submit expenses
- **Transparent**: Clear delegation status ("Acting as...")
- **Trustworthy**: Visible consent and audit trail

#### For Managers
- **Efficient**: Approval queue with delegation support
- **Controlled**: Grant/revoke delegations easily
- **Auditable**: See who did what on whose behalf

#### For Administrators
- **Secure**: Strict tenant isolation by default
- **Compliant**: Complete audit trail
- **Manageable**: Policy-as-code with OPA

## Business Value

### Primary Value: Learning Platform
- Hands-on experience with advanced IAM patterns
- Reference code for production implementations
- Architectural patterns that can be adapted

### Secondary Value: Compliance Showcase
- Demonstrates GDPR-ready consent management
- Shows SOX-compliant audit trails
- Illustrates purpose limitation principles

### Tertiary Value: Integration Blueprint
- Shows Keycloak + Spring Security integration
- Demonstrates OPA policy engine usage
- Provides Neo4j delegation modeling example

## Domain Context: Travel & Expense

### Why This Domain?

Travel & Expense (T&E) was chosen because it **naturally requires** delegation patterns:

1. **Executive Assistants** booking travel for executives
2. **Managers** approving expenses on behalf of other managers
3. **Finance Teams** accessing data across organizational boundaries
4. **External Partners** (travel agencies) acting with limited scope

These are real business needs, not artificial identity scenarios.

### Domain Entities

**Primary:**
- **Bookings** - Travel reservations (flights, hotels, cars)
- **Expenses** - Expense claims linked to bookings
- **Approvals** - Workflow states for booking/expense approval

**Identity:**
- **Delegations** - Who can act on behalf of whom
- **Consents** - Granted permissions with purpose and scope
- **Audit Entries** - Immutable log of identity-sensitive actions

## User Personas

### Alice (Standard Employee)
- Travels occasionally for business
- Books own travel and submits expenses
- **Identity need**: Basic authentication and authorization

### Bob (Manager)
- Manages team of 5-10 employees
- Approves team expenses
- Sometimes delegates approval to peers
- **Identity need**: Role-based access + delegation

### Carol (Executive)
- Travels frequently
- Has assistant (Dave) who manages her travel
- **Identity need**: Delegation grantor

### Dave (Executive Assistant)
- Books travel for multiple executives
- Needs to act on behalf of others
- Limited to specific actions (booking, not financial)
- **Identity need**: Delegated identity with consent

### Eve (Different Tenant)
- Works for different company on same platform
- Must be completely isolated from Alice's tenant
- **Identity need**: Multi-tenant isolation
