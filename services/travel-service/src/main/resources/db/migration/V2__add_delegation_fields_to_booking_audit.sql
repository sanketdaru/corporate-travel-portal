-- Flyway Migration for Travel Service
-- Version: V2
-- Description: Add delegation_id, consent_id, and tenant_id to booking_audit (ADR-011)
-- Date: 2026-03-29
--
-- ADR-011 requires every audit record to carry the full delegation identity chain:
--   delegation_id  → entry point into the Neo4j delegation graph for chain traversal
--   consent_id     → links to the consent record that authorised the action
--   tenant_id      → tenant scope, required for multi-tenant audit queries (ADR-003)
--
-- Both delegation_id and consent_id are nullable: they are NULL when an action is
-- performed directly by a user with no active delegation.
--
-- tenant_id is NOT NULL: every audit record must be scoped to a tenant.
-- Safe to add as NOT NULL because booking_audit has no rows yet
-- (service layer audit logging was not implemented prior to this migration).

ALTER TABLE travel.booking_audit
    ADD COLUMN delegation_id UUID,
    ADD COLUMN consent_id    UUID,
    ADD COLUMN tenant_id     VARCHAR(255) NOT NULL DEFAULT '';

-- Remove the DEFAULT now that the column exists; future inserts must supply the value.
ALTER TABLE travel.booking_audit
    ALTER COLUMN tenant_id DROP DEFAULT;

-- Indexes to support audit queries filtered by delegation or tenant
CREATE INDEX idx_booking_audit_delegation ON travel.booking_audit(delegation_id) WHERE delegation_id IS NOT NULL;
CREATE INDEX idx_booking_audit_tenant     ON travel.booking_audit(tenant_id);

-- Column documentation
COMMENT ON COLUMN travel.booking_audit.delegation_id IS
    'UUID of the delegation record in delegation-service. NULL when no delegation is active. '
    'Entry point into the Neo4j graph for full delegation chain reconstruction (ADR-011).';

COMMENT ON COLUMN travel.booking_audit.consent_id IS
    'UUID of the consent record that authorised this action. NULL when no delegation is active. '
    'Forwarded from employee-bff via DelegationContext (ADR-011).';

COMMENT ON COLUMN travel.booking_audit.tenant_id IS
    'Tenant scope of the audited action. Required for multi-tenant audit queries (ADR-003, ADR-011).';
