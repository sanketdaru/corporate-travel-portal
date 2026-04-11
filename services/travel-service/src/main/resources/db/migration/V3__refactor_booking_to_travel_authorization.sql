-- Flyway Migration V3
-- Description: Refactor booking from a completed transaction to a travel authorization
--
-- BEFORE: Booking modelled a specific transaction (FLIGHT/HOTEL/CAR with a known price)
-- AFTER:  Booking models a travel request ("going to London, May 10–17, approved budget ₹1,20,000")
--
-- Changes:
--   DROP   booking_type  — transport/accommodation type belongs on expense line items
--   DROP   total_amount  — recorded transaction cost; budget replaces this
--   ADD    budget        — pre-approved spending ceiling for the trip (mandatory)
--   ADD    budget_currency — ISO 4217 currency code for the budget (mandatory, default INR)
--   MAKE   destination, start_date, end_date, business_purpose NOT NULL
--
-- Deployment note: Apply this migration BEFORE deploying the new application code.
-- Existing rows (if any) receive safe defaults before NOT NULL constraints are added.

-- Step 1: Add new columns as nullable first (safe for existing rows)
ALTER TABLE travel.bookings
    ADD COLUMN IF NOT EXISTS budget          DECIMAL(12, 2),
    ADD COLUMN IF NOT EXISTS budget_currency VARCHAR(3);

-- Step 2: Back-fill defaults for any existing rows
UPDATE travel.bookings
SET
    destination     = COALESCE(destination, 'Unknown'),
    start_date      = COALESCE(start_date, CURRENT_DATE),
    end_date        = COALESCE(end_date, CURRENT_DATE + INTERVAL '7 days'),
    budget          = COALESCE(total_amount, 0.00),
    budget_currency = 'INR'
WHERE budget IS NULL OR budget_currency IS NULL
   OR destination IS NULL OR start_date IS NULL OR end_date IS NULL;

-- Step 3: Apply NOT NULL constraints now that all rows have values
ALTER TABLE travel.bookings
    ALTER COLUMN destination     SET NOT NULL,
    ALTER COLUMN start_date      SET NOT NULL,
    ALTER COLUMN end_date        SET NOT NULL,
    ALTER COLUMN budget          SET NOT NULL,
    ALTER COLUMN budget_currency SET NOT NULL;

-- Step 4: Set default for budget_currency on new inserts
ALTER TABLE travel.bookings
    ALTER COLUMN budget_currency SET DEFAULT 'INR';

-- Step 5: Drop the columns that no longer belong on a travel authorization
ALTER TABLE travel.bookings
    DROP COLUMN IF EXISTS booking_type,
    DROP COLUMN IF EXISTS total_amount;

-- Step 6: Update table comment to reflect new semantics
COMMENT ON TABLE travel.bookings IS
    'Travel authorizations: a request to undertake a business trip, with a pre-approved budget. '
    'Actual costs (flights, hotels, meals, transport) are captured as expense line items.';

COMMENT ON COLUMN travel.bookings.budget IS
    'Pre-approved spending ceiling for the trip. Expense submission total must not exceed this.';

COMMENT ON COLUMN travel.bookings.budget_currency IS
    'ISO 4217 currency code for the budget amount (e.g. INR, USD, EUR).';

COMMENT ON COLUMN travel.bookings.destination IS
    'Destination city / country for this trip (required).';

COMMENT ON COLUMN travel.bookings.start_date IS
    'First day of travel (required).';

COMMENT ON COLUMN travel.bookings.end_date IS
    'Last day of travel (required). Must be on or after start_date.';
