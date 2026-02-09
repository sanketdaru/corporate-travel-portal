-- Travel Service Tables
CREATE TABLE IF NOT EXISTS travel.bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    booking_type VARCHAR(50) NOT NULL,
    destination VARCHAR(255),
    start_date DATE,
    end_date DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    total_amount DECIMAL(10, 2),
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS travel.booking_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES travel.bookings(id),
    actor_id VARCHAR(255) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    details JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bookings_tenant ON travel.bookings(tenant_id);
CREATE INDEX idx_bookings_user ON travel.bookings(user_id);
CREATE INDEX idx_booking_audit_booking ON travel.booking_audit(booking_id);

-- Expense Service Tables
CREATE TABLE IF NOT EXISTS expense.expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    booking_id UUID,
    category VARCHAR(100) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description TEXT,
    receipt_url VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    submission_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS expense.expense_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id UUID NOT NULL REFERENCES expense.expenses(id),
    actor_id VARCHAR(255) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    details JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_expenses_tenant ON expense.expenses(tenant_id);
CREATE INDEX idx_expenses_user ON expense.expenses(user_id);
CREATE INDEX idx_expenses_booking ON expense.expenses(booking_id);
CREATE INDEX idx_expense_audit_expense ON expense.expense_audit(expense_id);

-- Approval Service Tables
CREATE TABLE IF NOT EXISTS approval.workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    requester_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(entity_type, entity_id)
);

CREATE TABLE IF NOT EXISTS approval.approval_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES approval.workflows(id) ON DELETE CASCADE,
    step_order INT NOT NULL,
    approver_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    acted_by VARCHAR(255),
    acted_at TIMESTAMP,
    comments TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workflows_tenant ON approval.workflows(tenant_id);
CREATE INDEX idx_workflows_entity ON approval.workflows(entity_type, entity_id);
CREATE INDEX idx_approval_steps_workflow ON approval.approval_steps(workflow_id);
CREATE INDEX idx_approval_steps_approver ON approval.approval_steps(approver_id);

-- Consent Service Tables
CREATE TABLE IF NOT EXISTS consent.consents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    grantor_id VARCHAR(255) NOT NULL,
    grantee_id VARCHAR(255) NOT NULL,
    purpose VARCHAR(255) NOT NULL,
    scope JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    revoked_at TIMESTAMP,
    metadata JSONB
);

CREATE TABLE IF NOT EXISTS consent.consent_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consent_id UUID NOT NULL REFERENCES consent.consents(id),
    action VARCHAR(50) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    details JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_consents_tenant ON consent.consents(tenant_id);
CREATE INDEX idx_consents_grantor ON consent.consents(grantor_id);
CREATE INDEX idx_consents_grantee ON consent.consents(grantee_id);
CREATE INDEX idx_consent_audit_consent ON consent.consent_audit(consent_id);

-- Delegation Service Tables (minimal - graph data in Neo4j)
CREATE TABLE IF NOT EXISTS delegation.delegation_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    delegator_id VARCHAR(255) NOT NULL,
    delegate_id VARCHAR(255) NOT NULL,
    neo4j_relationship_id VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    metadata JSONB
);

CREATE INDEX idx_delegation_tenant ON delegation.delegation_metadata(tenant_id);
CREATE INDEX idx_delegation_delegator ON delegation.delegation_metadata(delegator_id);
CREATE INDEX idx_delegation_delegate ON delegation.delegation_metadata(delegate_id);
