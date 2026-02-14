package com.corporate.travel.travel.service.impl;

import com.corporate.travel.models.BookingStatus;
import com.corporate.travel.security.OpaClient;
import com.corporate.travel.security.SecurityContext;
import com.corporate.travel.travel.exception.BookingNotFoundException;
import com.corporate.travel.travel.model.entity.Booking;
import com.corporate.travel.travel.repository.BookingRepository;
import com.corporate.travel.travel.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of BookingService with OPA authorization
 * 
 * Key patterns:
 * 1. Extract tenant/user from SecurityContext
 * 2. Load resource from database
 * 3. Build OPA input with user + resource context
 * 4. Call OPA for authorization
 * 5. Execute business logic if authorized
 * 6. (Audit logging would go here)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BookingServiceImpl implements BookingService {
    
    private final BookingRepository bookingRepository;
    private final OpaClient opaClient;
    
    @Override
    public Booking createBooking(Booking booking, SecurityContext context) {
        log.info("Creating booking for user: {}, tenant: {}", context.getUserId(), context.getTenantId());
        
        // Set tenant and user from context (enforce ownership)
        booking.setTenantId(context.getTenantId());
        
        // In delegation scenarios, userId is the subject (person on whose behalf)
        // For now, we use the context's subject if present, otherwise the user
        String ownerId = context.getSubjectId() != null ? context.getSubjectId() : context.getUserId();
        booking.setUserId(ownerId);
        
        // Track who created it (actor in delegation scenarios)
        booking.setCreatedBy(context.getUserId());
        booking.setUpdatedBy(context.getUserId());
        
        // Set initial status if not provided
        if (booking.getStatus() == null) {
            booking.setStatus(BookingStatus.PENDING);
        }
        
        // Authorization check - can user create bookings?
        Map<String, Object> resource = Map.of(
            "type", "booking",
            "tenant_id", booking.getTenantId(),
            "user_id", booking.getUserId()
        );
        
        if (!opaClient.authorize(context, "create_booking", resource)) {
            log.warn("Authorization denied for user {} to create booking in tenant {}", 
                context.getUserId(), context.getTenantId());
            throw new AccessDeniedException("Not authorized to create bookings");
        }
        
        Booking saved = bookingRepository.save(booking);
        log.info("Booking created with ID: {}", saved.getId());
        
        return saved;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Booking getBooking(UUID id, SecurityContext context) {
        log.debug("Fetching booking {} for user {}", id, context.getUserId());
        
        // Load booking with tenant check for isolation
        Booking booking = bookingRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new BookingNotFoundException(id));
        
        // Build resource context for OPA
        Map<String, Object> resource = Map.of(
            "type", "booking",
            "id", booking.getId().toString(),
            "tenant_id", booking.getTenantId(),
            "user_id", booking.getUserId(),
            "status", booking.getStatus().toString()
        );
        
        // Check authorization with OPA
        if (!opaClient.authorize(context, "view_booking", resource)) {
            log.warn("Authorization denied for user {} to view booking {}", 
                context.getUserId(), id);
            throw new AccessDeniedException("Not authorized to view this booking");
        }
        
        return booking;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Booking> getUserBookings(SecurityContext context) {
        log.debug("Fetching bookings for user {} in tenant {}", 
            context.getUserId(), context.getTenantId());
        
        // Determine which user's bookings to fetch
        // In delegation scenarios, fetch the subject's bookings
        String targetUserId = context.getSubjectId() != null ? 
            context.getSubjectId() : context.getUserId();
        log.debug("context: {}", context);
        log.debug("targetUserId: {}", targetUserId);
        
        // Authorization check - can user list bookings?
        Map<String, Object> resource = Map.of(
            "type", "booking",
            "tenant_id", context.getTenantId(),
            "user_id", targetUserId
        );
        
        if (!opaClient.authorize(context, "view_booking", resource)) {
            log.warn("Authorization denied for user {} to list bookings", context.getUserId());
            throw new AccessDeniedException("Not authorized to list bookings");
        }
        
        return bookingRepository.findByTenantIdAndUserId(
            context.getTenantId(), 
            targetUserId
        );
    }
    
    @Override
    public Booking updateBookingStatus(UUID id, BookingStatus status, SecurityContext context) {
        log.info("Updating booking {} status to {}", id, status);
        
        // Load booking with tenant check
        Booking booking = bookingRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new BookingNotFoundException(id));
        
        // Build resource context
        Map<String, Object> resource = Map.of(
            "type", "booking",
            "id", booking.getId().toString(),
            "tenant_id", booking.getTenantId(),
            "user_id", booking.getUserId(),
            "current_status", booking.getStatus().toString(),
            "new_status", status.toString()
        );
        
        // Check authorization
        if (!opaClient.authorize(context, "update_booking", resource)) {
            log.warn("Authorization denied for user {} to update booking {}", 
                context.getUserId(), id);
            throw new AccessDeniedException("Not authorized to update this booking");
        }
        
        // Update status
        booking.setStatus(status);
        booking.setUpdatedBy(context.getUserId());
        
        Booking updated = bookingRepository.save(booking);
        log.info("Booking {} status updated to {}", id, status);
        
        return updated;
    }
    
    @Override
    public void deleteBooking(UUID id, SecurityContext context) {
        log.info("Deleting booking {} by user {}", id, context.getUserId());
        
        // Load booking with tenant check
        Booking booking = bookingRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new BookingNotFoundException(id));
        
        // Build resource context
        Map<String, Object> resource = Map.of(
            "type", "booking",
            "id", booking.getId().toString(),
            "tenant_id", booking.getTenantId(),
            "user_id", booking.getUserId(),
            "status", booking.getStatus().toString()
        );
        
        // Check authorization
        if (!opaClient.authorize(context, "delete_booking", resource)) {
            log.warn("Authorization denied for user {} to delete booking {}", 
                context.getUserId(), id);
            throw new AccessDeniedException("Not authorized to delete this booking");
        }
        
        // Delete the booking
        bookingRepository.delete(booking);
        log.info("Booking {} deleted", id);
    }
}
