package com.corporate.travel.delegation.repository.jpa;

import com.corporate.travel.delegation.model.entity.Delegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for Delegation entities (PostgreSQL)
 * 
 * Provides CRUD operations and tenant-aware queries
 */
@Repository
public interface DelegationRepository extends JpaRepository<Delegation, UUID> {

    /**
     * Find all delegations for a specific tenant
     */
    List<Delegation> findByTenantId(String tenantId);

    /**
     * Find all delegations where user is the delegator (granting delegations)
     */
    List<Delegation> findByTenantIdAndDelegatorId(String tenantId, String delegatorId);

    /**
     * Find all delegations where user is the delegate (receiving delegations)
     */
    List<Delegation> findByTenantIdAndDelegateId(String tenantId, String delegateId);

    /**
     * Find a specific delegation by ID and tenant (for authorization)
     */
    Optional<Delegation> findByIdAndTenantId(UUID id, String tenantId);

    /**
     * Find all active delegations for a tenant
     */
    List<Delegation> findByTenantIdAndActiveTrue(String tenantId);

    /**
     * Find all active delegations where user is delegate
     */
    List<Delegation> findByTenantIdAndDelegateIdAndActiveTrue(String tenantId, String delegateId);

    /**
     * Find all active delegations where user is delegator
     */
    List<Delegation> findByTenantIdAndDelegatorIdAndActiveTrue(String tenantId, String delegatorId);

    /**
     * Find active delegation between two specific users
     */
    @Query("SELECT d FROM Delegation d WHERE d.tenantId = :tenantId " +
           "AND d.delegatorId = :delegatorId AND d.delegateId = :delegateId " +
           "AND d.active = true")
    Optional<Delegation> findActiveDelegation(
        @Param("tenantId") String tenantId,
        @Param("delegatorId") String delegatorId,
        @Param("delegateId") String delegateId
    );

    /**
     * Find all expired delegations that are still marked as active
     */
    @Query("SELECT d FROM Delegation d WHERE d.active = true " +
           "AND d.expiresAt IS NOT NULL AND d.expiresAt < :now")
    List<Delegation> findExpiredDelegations(@Param("now") LocalDateTime now);

    /**
     * Check if an active delegation exists between two users
     */
    @Query("SELECT COUNT(d) > 0 FROM Delegation d WHERE d.tenantId = :tenantId " +
           "AND d.delegatorId = :delegatorId AND d.delegateId = :delegateId " +
           "AND d.active = true " +
           "AND (d.expiresAt IS NULL OR d.expiresAt > :now)")
    boolean existsActiveDelegation(
        @Param("tenantId") String tenantId,
        @Param("delegatorId") String delegatorId,
        @Param("delegateId") String delegateId,
        @Param("now") LocalDateTime now
    );
}