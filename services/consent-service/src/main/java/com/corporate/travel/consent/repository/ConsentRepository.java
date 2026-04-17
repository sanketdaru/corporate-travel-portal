package com.corporate.travel.consent.repository;

import com.corporate.travel.consent.model.entity.Consent;
import com.corporate.travel.consent.model.entity.ConsentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Consent entity with tenant-aware queries
 */
@Repository
public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    /**
     * Find consent by ID and tenant ID (tenant isolation)
     */
    Optional<Consent> findByIdAndTenantId(UUID id, String tenantId);

    /**
     * Find all consents granted by a user within their tenant
     */
    List<Consent> findByGrantorIdAndTenantIdOrderByGrantedAtDesc(String grantorId, String tenantId);

    /**
     * Find all consents granted to a user within their tenant
     */
    List<Consent> findByGranteeIdAndTenantIdOrderByGrantedAtDesc(String granteeId, String tenantId);

    /**
     * Find active consents by grantor, grantee, and purpose
     */
    @Query("SELECT c FROM Consent c WHERE c.grantorId = :grantorId AND c.granteeId = :granteeId " +
           "AND c.purpose = :purpose AND c.status = 'ACTIVE' AND c.tenantId = :tenantId " +
           "AND (c.expiresAt IS NULL OR c.expiresAt > CURRENT_TIMESTAMP)")
    List<Consent> findActiveConsents(@Param("grantorId") String grantorId,
                                     @Param("granteeId") String granteeId,
                                     @Param("purpose") String purpose,
                                     @Param("tenantId") String tenantId);

    /**
     * Find active consents by delegation ID
     */
    List<Consent> findByDelegationIdAndStatusAndTenantId(UUID delegationId, ConsentStatus status, String tenantId);

    /**
     * Check if an active, non-expired duplicate consent exists
     */
    @Query("SELECT COUNT(c) > 0 FROM Consent c WHERE c.grantorId = :grantorId AND c.granteeId = :granteeId " +
           "AND c.purpose = :purpose AND c.status = 'ACTIVE' AND c.tenantId = :tenantId " +
           "AND (c.expiresAt IS NULL OR c.expiresAt > CURRENT_TIMESTAMP)")
    boolean existsActiveDuplicateConsent(@Param("grantorId") String grantorId,
                                         @Param("granteeId") String granteeId,
                                         @Param("purpose") String purpose,
                                         @Param("tenantId") String tenantId);

    /**
     * Find expired consents (status=ACTIVE but expiresAt in the past) for a specific pair and purpose
     */
    @Query("SELECT c FROM Consent c WHERE c.grantorId = :grantorId AND c.granteeId = :granteeId " +
           "AND c.purpose = :purpose AND c.status = 'ACTIVE' AND c.tenantId = :tenantId " +
           "AND c.expiresAt IS NOT NULL AND c.expiresAt <= CURRENT_TIMESTAMP")
    List<Consent> findExpiredConsentsForPair(@Param("grantorId") String grantorId,
                                             @Param("granteeId") String granteeId,
                                             @Param("purpose") String purpose,
                                             @Param("tenantId") String tenantId);

    /**
     * Find all expired consents that need status update
     */
    @Query("SELECT c FROM Consent c WHERE c.status = 'ACTIVE' AND c.expiresAt IS NOT NULL " +
           "AND c.expiresAt <= CURRENT_TIMESTAMP")
    List<Consent> findExpiredConsents();
}