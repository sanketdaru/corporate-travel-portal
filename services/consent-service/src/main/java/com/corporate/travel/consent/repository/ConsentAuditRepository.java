package com.corporate.travel.consent.repository;

import com.corporate.travel.consent.model.entity.ConsentAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for ConsentAudit entity
 */
@Repository
public interface ConsentAuditRepository extends JpaRepository<ConsentAudit, UUID> {

    /**
     * Find all audit records for a specific consent, ordered by timestamp descending
     * Note: This method does not include tenant isolation - use for test scenarios only
     * or when consent ID is already verified to belong to the correct tenant
     */
    List<ConsentAudit> findByConsentIdOrderByTimestampDesc(UUID consentId);

    /**
     * Find all audit records for a specific consent, ordered by timestamp descending
     */
    List<ConsentAudit> findByConsentIdAndTenantIdOrderByTimestampDesc(UUID consentId, String tenantId);

    /**
     * Find all audit records for a tenant, ordered by timestamp descending
     */
    List<ConsentAudit> findByTenantIdOrderByTimestampDesc(String tenantId);
}
