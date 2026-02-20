# Flyway Migration Guide for Expense Service

## Overview

Expense Service now uses Flyway for database schema migration management. The schema is defined by the Expense.java and ExpenseItem.java entities and managed through versioned SQL migrations.

## Configuration

### Dependencies (build.gradle)
```gradle
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

### Application Configuration (application.yml)
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    locations: classpath:db/migration
    schemas: expense
```

## Migration Files

Migrations are located in: `src/main/resources/db/migration/`

### V1: Baseline Migration
- **File**: `V1__baseline_expenses_schema.sql`
- **Description**: Initial schema with expenses, expense_items, and expense_audit tables
- **Source**: Generated from Expense.java and ExpenseItem.java entities as source of truth
- **Created**: 2026-02-20

## Schema Structure

### expenses table
Main expense report table containing:
- Multi-tenant isolation (tenant_id)
- Ownership tracking (user_id, created_by, updated_by)
- Optional booking reference (booking_id)
- Expense details (title, description, total_amount, currency)
- Workflow status (DRAFT, SUBMITTED, APPROVED, REJECTED, PAID)
- Approval tracking (submission_date, approval_date, approver_id)

### expense_items table
Line items for expense reports:
- Reference to parent expense (expense_id with CASCADE delete)
- Item details (date, category, description, amount)
- Receipt tracking (receipt_url)
- Categories: TRAVEL, MEALS, ACCOMMODATION, TRANSPORTATION, OTHER

### expense_audit table
Audit trail for compliance:
- Actor/subject tracking for delegation scenarios
- Action types: CREATE, UPDATE, DELETE, SUBMIT, APPROVE, REJECT, PAY
- Additional context stored as JSONB

## Testing the Migration

### Step 1: Clean Existing Schema (if needed)

Connect to PostgreSQL and drop the expense schema:

```sql
-- Connect to database
docker-compose exec postgres psql -U admin -d corporate_travel

-- Drop existing expense schema (clean start)
DROP SCHEMA IF EXISTS expense CASCADE;

-- Exit psql
\q
```

### Step 2: Start Expense Service

The service will automatically:
1. Create the `expense` schema if it doesn't exist
2. Run Flyway migrations
3. Create `flyway_schema_history` table
4. Execute V1__baseline_expenses_schema.sql
5. Create `expenses`, `expense_items`, and `expense_audit` tables with indexes

```bash
# Start with Docker
docker-compose up -d expense-service

# Or run locally
./gradlew :services:expense-service:bootRun
```

### Step 3: Verify Schema Creation

Connect to PostgreSQL and verify:

```sql
-- Connect to database
docker-compose exec postgres psql -U admin -d corporate_travel

-- Switch to expense schema
SET search_path TO expense;

-- Check Flyway history
SELECT * FROM flyway_schema_history;

-- Expected output:
-- installed_rank | version | description              | type | script                           | checksum    | installed_by | installed_on | execution_time | success
-- 1              | 1       | baseline expenses schema | SQL  | V1__baseline_expenses_schema.sql | <checksum>  | admin        | <timestamp>  | <ms>          | true

-- Verify tables exist
\dt

-- Expected output:
-- Schema  | Name            | Type  | Owner
-- expense | expenses        | table | admin
-- expense | expense_items   | table | admin
-- expense | expense_audit   | table | admin
-- expense | flyway_schema_history | table | admin

-- Verify expenses table structure
\d expenses

-- Verify expense_items table structure
\d expense_items

-- Verify expense_audit table structure
\d expense_audit

-- Verify indexes
\di

-- Exit psql
\q
```

## Future Schema Changes

### Creating a New Migration

1. Create a new migration file with next version number:
   ```
   V2__add_reimbursement_date.sql
   V3__add_expense_category_index.sql
   V4__add_receipt_required_flag.sql
   ```

2. Write the SQL changes:
   ```sql
   -- V2__add_reimbursement_date.sql
   ALTER TABLE expenses ADD COLUMN reimbursement_date TIMESTAMP;
   CREATE INDEX idx_expenses_reimbursement ON expenses(reimbursement_date);
   
   COMMENT ON COLUMN expenses.reimbursement_date IS 'Date when expense was reimbursed to employee';
   ```

3. Restart the service - Flyway will automatically apply new migrations

### Migration Naming Convention

Format: `V{version}__{description}.sql`

- **Version**: Sequential integer (V1, V2, V3, ...)
- **Description**: Underscore-separated, lowercase
- **Examples**:
  - `V2__add_reimbursement_tracking.sql`
  - `V3__create_expense_templates_table.sql`
  - `V4__add_indexes_for_reporting.sql`

## Troubleshooting

### Migration Failed

If a migration fails:

1. Check Flyway status:
   ```sql
   SELECT * FROM expense.flyway_schema_history WHERE success = false;
   ```

2. Fix the migration SQL file

3. Repair Flyway:
   ```bash
   # Connect to service and run Flyway repair
   docker-compose exec expense-service ./gradlew flywayRepair
   ```

4. Restart the service

### Schema Out of Sync

If manual changes were made to the database:

1. Clean the schema:
   ```sql
   DROP SCHEMA expense CASCADE;
   ```

2. Restart the service - Flyway will recreate from scratch

### Baseline Existing Database

If you need to baseline an existing database:

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 1  # Set to current version
```

## Benefits of Flyway

✅ **Version Control**: Complete history in `flyway_schema_history` table  
✅ **Automated Execution**: No manual script running  
✅ **Environment Consistency**: Same migrations applied everywhere  
✅ **Developer-Friendly**: Simple SQL-based workflow  
✅ **Audit Trail**: Tracks who, when, and what changed  
✅ **Rollback Support**: Can repair failed migrations  
✅ **CI/CD Ready**: Easy pipeline integration

## Expense Service Specifics

### Three-Table Structure
Unlike travel-service with 2 tables, expense-service manages 3 tables:
1. **expenses** - Main expense reports
2. **expense_items** - Line items with CASCADE delete
3. **expense_audit** - Audit trail for compliance

### Workflow States
The expense status field tracks the approval workflow:
- **DRAFT**: Being created/edited
- **SUBMITTED**: Submitted for approval
- **APPROVED**: Approved by manager
- **REJECTED**: Rejected by manager
- **PAID**: Reimbursement completed

### Relationship to Bookings
Expenses can optionally reference travel bookings via `booking_id` column, enabling tracking of travel-related expenses.

## Related Documentation

- [ADR-023: Adopt Flyway for Database Schema Migration Management](../../architecture-decision-records/ADR-023:%20Adopt%20Flyway%20for%20Database%20Schema%20Migration%20Management.md)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Spring Boot Flyway Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)
- [Travel Service Flyway Guide](../travel-service/FLYWAY-MIGRATION.md)

## Notes

- **JPA ddl-auto**: Set to `validate` - Flyway is the source of truth
- **Baseline Version**: Set to 0 to allow baselining
- **Schema**: Each service manages its own schema (travel, expense, approval, etc.)
- **Audit Table**: Structure created but logging not yet implemented in service layer
- **Cascade Deletes**: expense_items and expense_audit use CASCADE delete when parent expense is deleted