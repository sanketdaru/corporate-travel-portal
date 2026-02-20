-- Flyway Baseline Migration for Expense Service
-- Version: V1
-- Description: Initial schema with expenses, expense_items, and expense_audit tables
-- Source: Generated from Expense.java and ExpenseItem.java entities as source of truth
-- Date: 2026-02-20

-- ==============================================================================
-- Expenses Table
-- ==============================================================================
-- Stores expense reports with multiple line items
-- Multi-tenant support via tenant_id
-- Delegation support via user_id (subject) and created_by (actor)
-- Links to bookings via booking_id (optional)

CREATE TABLE expenses (
    -- Primary Key
    id UUID PRIMARY KEY,
    
    -- Multi-tenant Isolation
    tenant_id VARCHAR(255) NOT NULL,
    
    -- Ownership & Delegation
    user_id VARCHAR(255) NOT NULL,           -- Subject (owner of expense)
    created_by VARCHAR(255),                  -- Actor (who created it)
    updated_by VARCHAR(255),                  -- Actor (who last updated it)
    
    -- Booking Reference (optional)
    booking_id UUID,                          -- Optional link to travel.bookings
    
    -- Expense Report Information
    title VARCHAR(500),                       -- Title/purpose of expense report
    description TEXT,                         -- Detailed description
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,  -- Calculated from items
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',      -- ISO 4217 currency code
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',     -- DRAFT, SUBMITTED, APPROVED, REJECTED, PAID
    
    -- Approval Workflow
    submission_date TIMESTAMP,                -- When submitted for approval
    approval_date TIMESTAMP,                  -- When approved/rejected
    approver_id VARCHAR(255),                 -- Manager who approved/rejected
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- ==============================================================================
-- Expense Items Table
-- ==============================================================================
-- Line items for expense reports
-- Each item represents a single expense (meal, travel, accommodation, etc.)

CREATE TABLE expense_items (
    -- Primary Key
    id UUID PRIMARY KEY,
    
    -- Reference to parent expense
    expense_id UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    
    -- Item Details
    date DATE NOT NULL,                       -- Date of expense
    category VARCHAR(100) NOT NULL,           -- TRAVEL, MEALS, ACCOMMODATION, TRANSPORTATION, OTHER
    description TEXT NOT NULL,                -- Description of expense item
    amount DECIMAL(10, 2) NOT NULL,           -- Amount of this item
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',  -- ISO 4217 currency code
    receipt_url TEXT,                         -- URL to receipt image/document
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- ==============================================================================
-- Expense Audit Table
-- ==============================================================================
-- Tracks all changes to expenses with actor/subject information
-- Essential for compliance and delegation audit trail
-- NOTE: Audit logging not yet implemented in service layer

CREATE TABLE expense_audit (
    -- Primary Key
    id UUID PRIMARY KEY,
    
    -- Reference to expense
    expense_id UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    
    -- Actor/Subject Tracking (for delegation)
    actor_id VARCHAR(255) NOT NULL,           -- Who performed the action
    subject_id VARCHAR(255) NOT NULL,         -- On whose behalf
    
    -- Action Details
    action VARCHAR(100) NOT NULL,             -- CREATE, UPDATE, DELETE, SUBMIT, APPROVE, REJECT, PAY
    details JSONB,                             -- Additional context about the change
    
    -- Timestamp
    timestamp TIMESTAMP NOT NULL
);

-- ==============================================================================
-- Indexes for Performance
-- ==============================================================================

-- Expenses table indexes
CREATE INDEX idx_expenses_tenant ON expenses(tenant_id);
CREATE INDEX idx_expenses_user ON expenses(user_id);
CREATE INDEX idx_expenses_status ON expenses(status);
CREATE INDEX idx_expenses_booking ON expenses(booking_id);

-- Expense items table indexes
CREATE INDEX idx_expense_items_expense ON expense_items(expense_id);

-- Expense audit table indexes
CREATE INDEX idx_expense_audit_expense ON expense_audit(expense_id);

-- ==============================================================================
-- Table Comments (PostgreSQL Documentation)
-- ==============================================================================

COMMENT ON TABLE expenses IS 'Expense reports with multi-tenant isolation and delegation support';
COMMENT ON COLUMN expenses.tenant_id IS 'Tenant ID for multi-tenant isolation - every query must filter by this';
COMMENT ON COLUMN expenses.user_id IS 'Owner of the expense (subject in delegation scenarios)';
COMMENT ON COLUMN expenses.created_by IS 'User who created the expense (actor in delegation scenarios)';
COMMENT ON COLUMN expenses.booking_id IS 'Optional reference to related travel booking';
COMMENT ON COLUMN expenses.total_amount IS 'Calculated total from expense items';
COMMENT ON COLUMN expenses.status IS 'Workflow status: DRAFT, SUBMITTED, APPROVED, REJECTED, PAID';

COMMENT ON TABLE expense_items IS 'Line items for expense reports';
COMMENT ON COLUMN expense_items.category IS 'Expense category: TRAVEL, MEALS, ACCOMMODATION, TRANSPORTATION, OTHER';
COMMENT ON COLUMN expense_items.amount IS 'Amount of this individual expense item';

COMMENT ON TABLE expense_audit IS 'Audit trail for all expense changes with actor/subject tracking';
COMMENT ON COLUMN expense_audit.actor_id IS 'User who performed the action';
COMMENT ON COLUMN expense_audit.subject_id IS 'User on whose behalf the action was performed';

-- ==============================================================================
-- End of V1 Baseline Migration
-- ==============================================================================