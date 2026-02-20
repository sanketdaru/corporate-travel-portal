# ADR-023: Adopt Flyway for Database Schema Migration Management

## Status
Accepted - Implementation In Progress

**Implementation Status**:
- ✅ Travel Service: Flyway implemented (2026-02-18)
- ✅ Expense Service: Flyway implemented (2026-02-20)
- ⏳ Approval Service: Pending
- ⏳ Consent Service: Pending
- ⏳ Delegation Service: Pending

## Context

### Current State
The platform currently uses manual SQL initialization scripts located in `infrastructure/databases/init-scripts/`:
- `01-create-schemas.sql` - Creates database schemas for each service
- `02-create-tables.sql` - Creates initial table structures
- `03-update-expense-schema.sql` - Schema evolution updates

This approach has several limitations:

1. **No Version Control**: No tracking of which migrations have been applied to which environment
2. **Schema Drift Risk**: Different environments may have different schema states
3. **No Rollback Strategy**: Changes cannot be easily reverted
4. **Manual Execution**: Developers must manually run scripts in correct order
5. **No Audit Trail**: No history of when changes were applied or by whom
6. **Onboarding Friction**: New developers must understand script execution order
7. **CI/CD Challenges**: Difficult to automate schema updates in deployment pipelines
8. **Environment Inconsistency**: Dev, test, and prod schemas can diverge

### Requirements

The platform requires:
- **Developer-friendly**: Simple, SQL-based migration workflow
- **Version tracking**: Clear history of all schema changes
- **Environment consistency**: Identical schemas across dev, test, staging, prod
- **Automated execution**: Migrations run automatically on application startup (dev) or deployment (prod)
- **Multi-service support**: Each microservice manages its own schema (per ADR-006)
- **Spring Boot integration**: Native framework support (per ADR-013)
- **Baseline support**: Ability to initialize existing databases
- **Audit compliance**: Complete history of schema evolution
- **Rollback capability**: Support for migration failure recovery

## Decision

We will adopt **Flyway** as the standard database schema migration tool for all PostgreSQL databases across all services.

### Key Implementation Principles

1. **Schema-per-Service Pattern**: Each microservice manages migrations for its own schema:
   - Travel Service → `travel` schema
   - Expense Service → `expense` schema
   - Approval Service → `approval` schema
   - Consent Service → `consent` schema
   - Delegation Service → `delegation` schema

2. **Migration Organization**: Migrations stored within each service's resources:
   ```
   services/{service-name}/
   └── src/main/resources/
       └── db/migration/
           ├── V1__baseline.sql
           ├── V2__add_audit_columns.sql
           └── V3__add_indexes.sql
   ```

3. **Naming Convention**: `V{version}__{description}.sql`
   - Version: Sequential integer (V1, V2, V3, etc.)
   - Description: Underscore-separated, descriptive name
   - Example: `V1__create_bookings_table.sql`

4. **Baseline Strategy**: Use baseline for existing databases:
   - Production/existing databases: Baseline at current version
   - New installations: Apply all migrations from V1

5. **Environment-Specific Behavior**:
   - **Development**: Auto-migrate on startup (`flyway.enabled=true`)
   - **Test**: Clean and migrate for each test run
   - **Production**: Manual migration via CI/CD pipeline or startup

### Configuration Strategy

**application.yml (Base Configuration)**
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1
    validate-on-migrate: true
    locations: classpath:db/migration
    schemas: ${DB_SCHEMA}
```

**Per-Service Schema Configuration**
```yaml
# Travel Service
spring.flyway.schemas: travel

# Expense Service  
spring.flyway.schemas: expense

# Approval Service
spring.flyway.schemas: approval
```

**Production Configuration** (application-prod.yml)
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: false  # Strict mode
    validate-on-migrate: true
    out-of-order: false        # Enforce sequential migrations
```

### Migration from Current State

**Phase 1: Baseline Existing Schemas**
1. Take snapshot of current schema state
2. Create `V1__baseline.sql` for each service containing current schema
3. Configure `baseline-on-migrate=true`
4. Mark existing databases as baselined at V1

**Phase 2: Convert Future Changes**
1. New schema changes become versioned migrations (V2, V3, etc.)
2. Remove manual script execution from workflow
3. Update developer documentation

**Phase 3: Deprecate Init Scripts**
1. Keep `init-scripts/` for reference only
2. Add README indicating Flyway is now authoritative
3. Update docker-compose.yml to rely on Flyway

### Dependency Configuration

**build.gradle** (Each Service)
```gradle
dependencies {
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
}
```

Spring Boot will auto-configure Flyway when dependencies are present.

## Consequences

### Positive

1. **Version Control**: Complete history in `flyway_schema_history` table
   - Tracks version, description, execution time, checksum
   - Provides audit trail for compliance
   
2. **Automated Execution**: No manual script running
   - Migrations run on application startup (configurable)
   - Reduces human error
   
3. **Environment Consistency**: Same migrations applied everywhere
   - Dev, test, staging, prod use identical migrations
   - Eliminates schema drift
   
4. **Developer Experience**: Simple SQL-based workflow
   - Developers write standard SQL (no new syntax)
   - Clear versioning and naming conventions
   - Easy to understand and review
   
5. **Spring Boot Integration**: Native framework support
   - Auto-configuration out of the box
   - Integrated with application lifecycle
   - Consistent with ADR-013

6. **Rollback Support**: Multiple recovery strategies
   - Failed migrations prevent application startup
   - Can repair or manually rollback
   - Checksums detect manual changes

7. **CI/CD Ready**: Easy pipeline integration
   - Can run migrations before deployment
   - Can validate migrations in build
   - Can generate migration reports

8. **Multi-Service Support**: Each service owns its schema
   - Aligns with microservices pattern (ADR-006)
   - Independent schema evolution
   - No cross-service migration coordination needed

9. **Baseline Support**: Works with existing databases
   - Can adopt gradually
   - Doesn't require fresh database
   - Production-friendly adoption path

### Negative

1. **Learning Curve**: Team must understand Flyway concepts
   - Migration versioning rules
   - Baseline strategy
   - Checksum validation
   - Mitigation: Comprehensive documentation and examples

2. **Migration Coordination**: Cross-service schema changes require planning
   - Foreign key relationships between schemas
   - Breaking changes need coordination
   - Mitigation: Keep services loosely coupled

3. **Failed Migration Recovery**: Requires manual intervention
   - Flyway marks schema as failed
   - Must repair or rollback manually
   - Mitigation: Thorough testing in dev/test environments

4. **Database State Tracking**: Additional table per schema
   - `flyway_schema_history` adds overhead
   - Must be backed up with schema
   - Mitigation: Minimal overhead, valuable audit benefit

5. **Migration Immutability**: Applied migrations cannot be modified
   - Checksum validation prevents changes
   - Must create new migration to fix issues
   - Mitigation: Proper review process before applying

6. **Initial Migration**: Converting existing setup requires effort
   - Creating baseline migrations
   - Testing migration process
   - Updating documentation
   - Mitigation: One-time cost, long-term benefit

## Alternatives Considered

### Liquibase
**Pros**:
- More features (preconditions, contexts, labels)
- Multiple format support (XML, YAML, JSON, SQL)
- Better rollback capabilities
- Advanced change tracking

**Cons**:
- Steeper learning curve
- XML-heavy default (less readable)
- More complex configuration
- Overkill for current needs

**Decision**: Rejected due to complexity and XML overhead

### Manual SQL Scripts (Current Approach)
**Pros**:
- Simple and direct
- No additional dependencies
- Full SQL control

**Cons**:
- No version tracking
- No automation
- High error risk
- No audit trail
- Environment drift

**Decision**: Insufficient for production system

### JPA/Hibernate ddl-auto
**Pros**:
- Automatic schema generation
- No migration files needed
- Framework integrated

**Cons**:
- Not production-safe
- No version control
- Can cause data loss
- No audit trail
- Not recommended by Hibernate team

**Decision**: Explicitly rejected for production use

### Custom Migration Tool
**Pros**:
- Tailored to specific needs
- Full control

**Cons**:
- Development and maintenance burden
- Reinventing the wheel
- No community support
- Testing overhead

**Decision**: Not justified given Flyway maturity

## Implementation Roadmap

### Phase 1: Foundation (Week 1)
1. Add Flyway dependencies to all service build.gradle files
2. Configure Flyway in application.yml for each service
3. Create baseline migrations from existing schemas
4. Test baseline process in local environment

### Phase 2: Service Migration (Week 2)
1. ✅ **Travel Service**: Converted to Flyway (2026-02-18)
   - Baseline migration created
   - Configuration complete
   - Documentation added
2. ✅ **Expense Service**: Converted to Flyway (2026-02-20)
   - Baseline migration created with 3 tables
   - Configuration complete
   - Documentation added
3. ⏳ **Approval Service**: Implement with Flyway from start
4. ⏳ **Consent Service**: Implement with Flyway from start
5. ⏳ **Delegation Service**: Implement with Flyway from start

### Phase 3: Documentation (Week 2)
1. Update developer onboarding guide
2. Create migration authoring guide
3. Document common scenarios and troubleshooting
4. Update IMPLEMENTATION.md with Flyway patterns

### Phase 4: CI/CD Integration (Week 3)
1. Add migration validation to build pipeline
2. Configure production migration strategy
3. Create migration rollback procedures
4. Set up migration monitoring

### Phase 5: Deprecation (Week 3)
1. Archive init-scripts directory with README
2. Remove manual script execution from docker-compose.yml
3. Update all documentation references
4. Announce completion to team

## Related ADRs

- **ADR-006**: Microservices Architecture - Each service manages its own schema
- **ADR-013**: Spring Boot Framework - Native Flyway integration
- **ADR-015**: PostgreSQL Database - Primary database for migrations
- **ADR-012**: Kubernetes Deployment - Migration strategy for K8s environments

## References

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Spring Boot Flyway Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)
- [Flyway Best Practices](https://flywaydb.org/documentation/usage/bestpractices)
- [Database Refactoring Best Practices](https://www.liquibase.com/blog/database-refactoring)

## Decision Date
2026-02-18

## Decision Makers
- Technical Lead
- Backend Development Team
- Database Administrator
- DevOps Team