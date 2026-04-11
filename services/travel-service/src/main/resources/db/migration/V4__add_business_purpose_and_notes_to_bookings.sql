-- Flyway Migration V4
-- Description: Add business_purpose and notes fields to travel authorizations
--
-- These fields capture the purpose of the trip and any additional notes.
-- Both are optional (nullable).

ALTER TABLE travel.bookings
    ADD COLUMN IF NOT EXISTS business_purpose TEXT,
    ADD COLUMN IF NOT EXISTS notes            TEXT;

COMMENT ON COLUMN travel.bookings.business_purpose IS
    'Brief description of the business reason for this trip (e.g. Q2 client review meetings).';

COMMENT ON COLUMN travel.bookings.notes IS
    'Optional notes: visa requirements, special arrangements, preferences, etc.';
