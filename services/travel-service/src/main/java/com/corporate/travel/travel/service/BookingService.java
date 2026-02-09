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
}
