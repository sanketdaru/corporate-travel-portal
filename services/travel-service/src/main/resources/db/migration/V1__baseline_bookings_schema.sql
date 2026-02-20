-- Flyway Baseline Migration for Travel Service
-- Version: V1
-- Description: Initial schema with bookings and booking_audit tables
-- Source: Generated from Booking.java entity as source of truth
-- Date: 2026-02-18

-- ==============================================================================
-- Bookings Table
-- ==============================================================================
-- Stores all travel bookings (flights, hotels, car rentals)
-- Multi-tenant support via tenant_id
-- Delegation support via user_id (subject) and created_by (actor)

CREATE TABLE bookings (
    -- Primary Key
    id UUID PRIMARY KEY,
    
    -- Multi-tenant Isolation
    tenant_id VARCHAR(255) NOT NULL,
    
    -- Ownership & Delegation
    user_id VARCHAR(255) NOT NULL,           -- Subject (owner of booking)
    created_by VARCHAR(255),                  -- Actor (who created it)
    updated_by VARCHAR(255),                  -- Actor (who last updated it)
    
    -- Booking Information
    booking_type VARCHAR(50) NOT NULL,        -- FLIGHT, HOTEL, CAR
    destination VARCHAR(255),
    start_date DATE,
    end_date DATE,
    status VARCHAR(50) NOT NULL,              -- DRAFT, PENDING, CONFIRMED, CANCELLED, COMPLETED
    total_amount DECIMAL(10, 2),
    
    -- Additional Details (JSON)
    details JSONB,                             -- Flight numbers, hotel names, confirmation codes, etc.
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- ==============================================================================
-- Indexes for Performance
-- ==============================================================================
-- Multi-tenant isolation queries
CREATE INDEX idx_bookings_tenant ON bookings(tenant_id);

-- User ownership queries
CREATE INDEX idx_bookings_user ON bookings(user_id);

-- Combined tenant + user queries (most common)
CREATE INDEX idx_bookings_tenant_user ON bookings(tenant_id, user_id);

-- Status filtering
CREATE INDEX idx_bookings_status ON bookings(status);

-- ==============================================================================
-- Booking Audit Table
-- ==============================================================================
-- Tracks all changes to bookings with actor/subject information
-- Essential for compliance and delegation audit trail
-- NOTE: Audit logging not yet implemented in service layer

CREATE TABLE booking_audit (
    -- Primary Key
    id UUID PRIMARY KEY,
    
    -- Reference to booking
    booking_id UUID NOT NULL REFERENCES bookings(id),
    
    -- Actor/Subject Tracking (for delegation)
    actor_id VARCHAR(255) NOT NULL,           -- Who performed the action
    subject_id VARCHAR(255) NOT NULL,         -- On whose behalf
    
    -- Action Details
    action VARCHAR(100) NOT NULL,             -- CREATE, UPDATE, DELETE, STATUS_CHANGE
    details JSONB,                             -- Additional context about the change
    
    -- Timestamp
    timestamp TIMESTAMP NOT NULL
);

-- Index for audit queries
CREATE INDEX idx_booking_audit_booking ON booking_audit(booking_id);
CREATE INDEX idx_booking_audit_actor ON booking_audit(actor_id);
CREATE INDEX idx_booking_audit_timestamp ON booking_audit(timestamp);

-- ==============================================================================
-- Table Comments (PostgreSQL Documentation)
-- ==============================================================================

COMMENT ON TABLE bookings IS 'Travel bookings with multi-tenant isolation and delegation support';
COMMENT ON COLUMN bookings.tenant_id IS 'Tenant ID for multi-tenant isolation - every query must filter by this';
COMMENT ON COLUMN bookings.user_id IS 'Owner of the booking (subject in delegation scenarios)';
COMMENT ON COLUMN bookings.created_by IS 'User who created the booking (actor in delegation scenarios)';
COMMENT ON COLUMN bookings.details IS 'Additional booking details stored as JSON (flight numbers, hotel info, etc.)';

COMMENT ON TABLE booking_audit IS 'Audit trail for all booking changes with actor/subject tracking';
COMMENT ON COLUMN booking_audit.actor_id IS 'User who performed the action';
COMMENT ON COLUMN booking_audit.subject_id IS 'User on whose behalf the action was performed';

-- ==============================================================================
-- End of V1 Baseline Migration
-- ==============================================================================