package com.corporate.travel.travel.service;

import com.corporate.travel.models.BookingStatus;
import com.corporate.travel.security.SecurityContext;
import com.corporate.travel.travel.model.entity.Booking;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Booking operations
 * 
 * All methods require SecurityContext for:
 * - Multi-tenant isolation
 * - Authorization checks via OPA
 * - Actor/subject tracking
 */
public interface BookingService {
    
    /**
     * Create a new booking
     * 
     * @param booking Booking to create
     * @param context Security context with user/tenant info
     * @return Created booking with ID
     */
    Booking createBooking(Booking booking, SecurityContext context);
    
    /**
     * Get a specific booking by ID
     * 
     * @param id Booking ID
     * @param context Security context
     * @return Booking if found and authorized
     * @throws BookingNotFoundException if not found
     * @throws AccessDeniedException if not authorized
     */
    Booking getBooking(UUID id, SecurityContext context);
    
    /**
     * Get all bookings for the current user
     * 
     * @param context Security context
     * @return List of user's bookings
     */
    List<Booking> getUserBookings(SecurityContext context);
    
    /**
     * Update booking status (used for approval workflow integration)
     * 
     * @param id Booking ID
     * @param status New status
     * @param context Security context
     * @return Updated booking
     */
    Booking updateBookingStatus(UUID id, BookingStatus status, SecurityContext context);
    
    /**
     * Delete/cancel a booking
     *
     * @param id Booking ID
     * @param context Security context
     */
    void deleteBooking(UUID id, SecurityContext context);

    /**
     * Get all bookings in the tenant — admin only.
     *
     * @param context Security context (must have admin role)
     * @return All bookings in the tenant
     */
    List<Booking> getTenantBookings(SecurityContext context);

    /**
     * Internal lookup by ID within a tenant — no OPA authorization.
     * Used exclusively by the budget endpoint, which the expense service calls with
     * its service-account token (ADR-009). The caller supplies the tenant of the expense.
     *
     * @param id       Booking ID
     * @param tenantId Tenant the booking must belong to
     * @return Booking if found in that tenant
     * @throws BookingNotFoundException if not found, or found in another tenant
     */
    Booking getBookingForTenant(UUID id, String tenantId);
}
