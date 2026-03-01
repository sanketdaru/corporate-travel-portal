-- Consent Service Schema
-- This schema stores consent records for delegated access with purpose binding

-- Create consent schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS consent;

-- Set search path
SET search_path TO consent;

-- Consents table: Stores consent records
CREATE TABLE consents (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    grantor_id VARCHAR(255) NOT NULL,        -- Person giving consent
    grantee_id VARCHAR(255) NOT NULL,        -- Person receiving consent
    delegation_id UUID,                      -- Link to delegation (optional)
    purpose VARCHAR(500) NOT NULL,           -- "book_travel", "approve_expenses", etc.
    scopes TEXT[] NOT NULL,                  -- ["view_bookings", "create_bookings"]
    data_categories TEXT[],                  -- ["travel_data", "expense_data"]
    granted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(255),
    status VARCHAR(50) NOT NULL,             -- ACTIVE, REVOKED, EXPIRED
    metadata JSONB,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(255),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Consent audit table: Tracks all consent lifecycle events
CREATE TABLE consent_audit (
    id UUID PRIMARY KEY,
    consent_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,            -- GRANTED, USED, REVOKED, EXPIRED
    actor_id VARCHAR(255) NOT NULL,          -- Who performed the action
    subject_id VARCHAR(255),                 -- On whose behalf (for delegation)
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    details JSONB,
    tenant_id VARCHAR(255) NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_consents_tenant ON consents(tenant_id);
CREATE INDEX idx_consents_grantor ON consents(grantor_id);
CREATE INDEX idx_consents_grantee ON consents(grantee_id);
CREATE INDEX idx_consents_status ON consents(status);
CREATE INDEX idx_consents_delegation ON consents(delegation_id);
CREATE INDEX idx_consents_expires_at ON consents(expires_at);

CREATE INDEX idx_consent_audit_consent_id ON consent_audit(consent_id);
CREATE INDEX idx_consent_audit_tenant ON consent_audit(tenant_id);
CREATE INDEX idx_consent_audit_timestamp ON consent_audit(timestamp);

-- Comments
COMMENT ON TABLE consents IS 'Stores consent records with purpose binding for delegated access';
COMMENT ON TABLE consent_audit IS 'Audit trail for all consent lifecycle events';

COMMENT ON COLUMN consents.grantor_id IS 'User who grants the consent (e.g., Carol)';
COMMENT ON COLUMN consents.grantee_id IS 'User who receives the consent (e.g., Dave)';
COMMENT ON COLUMN consents.delegation_id IS 'Optional link to delegation record';
COMMENT ON COLUMN consents.purpose IS 'Business purpose for the consent (e.g., book_travel)';
COMMENT ON COLUMN consents.scopes IS 'Array of permissions granted (e.g., view_bookings, create_bookings)';
COMMENT ON COLUMN consents.data_categories IS 'Types of data accessible (e.g., travel_data, expense_data)';
COMMENT ON COLUMN consents.status IS 'Lifecycle status: ACTIVE, REVOKED, EXPIRED';