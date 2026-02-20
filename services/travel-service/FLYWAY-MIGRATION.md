# Flyway Migration Guide for Travel Service

## Overview

Travel Service now uses Flyway for database schema migration management. The schema is defined by the Booking.java entity and managed through versioned SQL migrations.

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
    schemas: travel
```

## Migration Files

Migrations are located in: `src/main/resources/db/migration/`

### V1: Baseline Migration
- **File**: `V1__baseline_bookings_schema.sql`
- **Description**: Initial schema with bookings and booking_audit tables
- **Source**: Generated from Booking.java entity as source of truth
- **Created**: 2026-02-18

## Testing the Migration

### Step 1: Clean Existing Schema (if needed)

Connect to PostgreSQL and drop the travel schema:

```sql
-- Connect to database
docker-compose exec postgres psql -U admin -d corporate_travel

-- Drop existing travel schema (clean start)
DROP SCHEMA IF EXISTS travel CASCADE;

-- Exit psql
\q
```

### Step 2: Start Travel Service

The service will automatically:
1. Create the `travel` schema if it doesn't exist
2. Run Flyway migrations
3. Create `flyway_schema_history` table
4. Execute V1__baseline_bookings_schema.sql
5. Create `bookings` and `booking_audit` tables with indexes

```bash
# Start with Docker
docker-compose up -d travel-service

# Or run locally
./gradlew :services:travel-service:bootRun
```

### Step 3: Verify Schema Creation

Connect to PostgreSQL and verify:

```sql
-- Connect to database
docker-compose exec postgres psql -U admin -d corporate_travel

-- Switch to travel schema
SET search_path TO travel;

-- Check Flyway history
SELECT * FROM flyway_schema_history;

-- Expected output:
-- installed_rank | version | description              | type | script                           | checksum    | installed_by | installed_on | execution_time | success
-- 1              | 1       | baseline bookings schema | SQL  | V1__baseline_bookings_schema.sql | <checksum>  | admin        | <timestamp>  | <ms>          | true

-- Verify tables exist
\dt

-- Expected output:
-- Schema | Name            | Type  | Owner
-- travel | bookings        | table | admin
-- travel | booking_audit   | table | admin
-- travel | flyway_schema_history | table | admin

-- Verify bookings table structure
\d bookings

-- Verify indexes
\di

-- Exit psql
\q
```

## Future Schema Changes

### Creating a New Migration

1. Create a new migration file with next version number:
   ```
   V2__add_cancellation_reason.sql
   V3__add_booking_status_index.sql
   ```

2. Write the SQL changes:
   ```sql
   -- V2__add_cancellation_reason.sql
   ALTER TABLE bookings ADD COLUMN cancellation_reason TEXT;
   ```

3. Restart the service - Flyway will automatically apply new migrations

### Migration Naming Convention

Format: `V{version}__{description}.sql`

- **Version**: Sequential integer (V1, V2, V3, ...)
- **Description**: Underscore-separated, lowercase
- **Examples**:
  - `V2__add_payment_status.sql`
  - `V3__create_booking_notes_table.sql`
  - `V4__add_indexes_for_performance.sql`

## Troubleshooting

### Migration Failed

If a migration fails:

1. Check Flyway status:
   ```sql
   SELECT * FROM travel.flyway_schema_history WHERE success = false;
   ```

2. Fix the migration SQL file

3. Repair Flyway:
   ```bash
   # Connect to service and run Flyway repair
   docker-compose exec travel-service ./gradlew flywayRepair
   ```

4. Restart the service

### Schema Out of Sync

If manual changes were made to the database:

1. Clean the schema:
   ```sql
   DROP SCHEMA travel CASCADE;
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

## Related Documentation

- [ADR-023: Adopt Flyway for Database Schema Migration Management](../../architecture-decision-records/ADR-023:%20Adopt%20Flyway%20for%20Database%20Schema%20Migration%20Management.md)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Spring Boot Flyway Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)

## Notes

- **JPA ddl-auto**: Set to `validate` - Flyway is the source of truth
- **Baseline Version**: Set to 0 to allow baselining
- **Schema**: Each service manages its own schema (travel, expense, approval, etc.)
- **Audit Table**: Structure created but logging not yet implemented in service layer