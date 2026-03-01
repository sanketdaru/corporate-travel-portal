-- Delegation Service Schema
-- Description: Stores delegation relationships where one user (delegator) grants permission 
--              to another user (delegate) to act on their behalf for specific purposes

CREATE SCHEMA IF NOT EXISTS delegation;

SET search_path TO delegation;

-- Main delegations table
CREATE TABLE delegations (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    
    -- Delegation parties
    delegator_id VARCHAR(255) NOT NULL,  -- Person granting delegation (e.g., Carol)
    delegate_id VARCHAR(255) NOT NULL,   -- Person receiving delegation (e.g., Dave)
    
    -- Delegation details
    purpose VARCHAR(500) NOT NULL,       -- Purpose: "book_travel", "approve_expenses", etc.
    scopes TEXT[] NOT NULL,              -- Specific permissions: ["view_bookings", "create_bookings"]
    
    -- Temporal aspects
    granted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,                -- Optional expiration
    
    -- Lifecycle
    active BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(255),
    
    -- Audit fields
    created_by VARCHAR(255) NOT NULL,    -- Who created this delegation
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(255),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Constraints
    CONSTRAINT chk_delegation_parties CHECK (delegator_id != delegate_id),
    CONSTRAINT chk_scopes_not_empty CHECK (array_length(scopes, 1) > 0)
);

-- Indexes for query performance
CREATE INDEX idx_delegations_tenant ON delegations(tenant_id);
CREATE INDEX idx_delegations_delegator ON delegations(tenant_id, delegator_id);
CREATE INDEX idx_delegations_delegate ON delegations(tenant_id, delegate_id);
CREATE INDEX idx_delegations_active ON delegations(active) WHERE active = TRUE;
CREATE INDEX idx_delegations_expires ON delegations(expires_at) WHERE expires_at IS NOT NULL;

-- Composite index for common queries
CREATE INDEX idx_delegations_lookup ON delegations(tenant_id, delegate_id, active);

-- Comments for documentation
COMMENT ON TABLE delegations IS 'Stores delegation relationships where delegator grants permission to delegate';
COMMENT ON COLUMN delegations.delegator_id IS 'User granting the delegation (e.g., Carol)';
COMMENT ON COLUMN delegations.delegate_id IS 'User receiving the delegation (e.g., Dave)';
COMMENT ON COLUMN delegations.purpose IS 'Business purpose for delegation (e.g., book_travel, approve_expenses)';
COMMENT ON COLUMN delegations.scopes IS 'Array of specific permissions granted';
