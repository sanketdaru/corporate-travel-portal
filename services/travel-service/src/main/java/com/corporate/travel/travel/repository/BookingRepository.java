package com.corporate.travel.travel.repository;

import com.corporate.travel.travel.model.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Booking entity
 * 
 * All queries must be tenant-aware to enforce multi-tenant isolation
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    
    /**
     * Find all bookings for a specific tenant and user
     * Used for listing a user's bookings
     */
    List<Booking> findByTenantIdAndUserId(String tenantId, String userId);
    
    /**
     * Find all bookings for a specific tenant
     * Used by admins to view all bookings in their tenant
     */
    List<Booking> findByTenantId(String tenantId);
    
    /**
     * Find a booking by ID and tenant ID
     * Critical for tenant isolation - never fetch without tenant check
     */
    Optional<Booking> findByIdAndTenantId(UUID id, String tenantId);
}
