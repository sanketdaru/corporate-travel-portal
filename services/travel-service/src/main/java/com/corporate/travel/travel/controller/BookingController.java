package com.corporate.travel.travel.controller;

import com.corporate.travel.models.BookingStatus;
import com.corporate.travel.security.JwtAuthenticationConverter;
import com.corporate.travel.security.SecurityContext;
import com.corporate.travel.travel.model.entity.Booking;
import com.corporate.travel.travel.model.entity.BookingAudit;
import com.corporate.travel.travel.service.BookingAuditService;
import com.corporate.travel.travel.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Booking operations
 * 
 * All endpoints require JWT authentication
 * Authorization is handled by the service layer via OPA
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Bookings", description = "Travel booking management API")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {
    
    private final BookingService bookingService;
    private final BookingAuditService bookingAuditService;
    
    /**
     * Create a new booking
     * 
     * POST /api/bookings
     */
    @Operation(
        summary = "Create a new booking",
        description = "Creates a new travel booking for the authenticated user or on behalf of another user (delegation)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Booking created successfully",
            content = @Content(schema = @Schema(implementation = Booking.class))),
        @ApiResponse(responseCode = "400", description = "Invalid booking data", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content)
    })
    @PostMapping
    public ResponseEntity<Booking> createBooking(
            @Parameter(description = "Booking details", required = true)
            @Valid @RequestBody Booking booking,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.info("Creating booking for user: {}, isDelegated: {}", context.getUserId(), context.isDelegated());

        Booking created = bookingService.createBooking(booking, context);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    /**
     * Get all bookings for the current user
     * 
     * GET /api/bookings
     */
    @Operation(
        summary = "Get all bookings",
        description = "Retrieves all bookings for the authenticated user with multi-tenant isolation"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bookings retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content)
    })
    @GetMapping
    public ResponseEntity<List<Booking>> getUserBookings(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.debug("Fetching bookings for user: {}, isDelegated: {}", context.getUserId(), context.isDelegated());
        
        List<Booking> bookings = bookingService.getUserBookings(context);
        return ResponseEntity.ok(bookings);
    }
    
    /**
     * Get all bookings in the tenant — admin role only.
     *
     * GET /api/bookings/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<Booking>> getAllTenantBookings(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        return ResponseEntity.ok(bookingService.getTenantBookings(context));
    }

    /**
     * Get a specific booking by ID
     *
     * GET /api/bookings/{id}
     */
    @Operation(
        summary = "Get booking by ID",
        description = "Retrieves a specific booking by its unique identifier. Authorization via OPA ensures proper access control."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Booking found",
            content = @Content(schema = @Schema(implementation = Booking.class))),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBooking(
            @Parameter(description = "Booking UUID", required = true)
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        log.debug("Fetching booking {} for user: {}", id, context.getUserId());
        
        Booking booking = bookingService.getBooking(id, context);
        return ResponseEntity.ok(booking);
    }
    
    /**
     * Update booking status
     * 
     * PUT /api/bookings/{id}/status
     */
    @Operation(
        summary = "Update booking status",
        description = "Updates the status of a booking (e.g., PENDING -> CONFIRMED -> COMPLETED or CANCELLED)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status updated successfully",
            content = @Content(schema = @Schema(implementation = Booking.class))),
        @ApiResponse(responseCode = "400", description = "Invalid status", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
    })
    @PutMapping("/{id}/status")
    public ResponseEntity<Booking> updateBookingStatus(
            @Parameter(description = "Booking UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Status update request with 'status' field (PENDING, CONFIRMED, COMPLETED, CANCELLED)", required = true)
            @RequestBody Map<String, String> request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, httpRequest);
        log.info("Updating booking {} status by user: {}", id, context.getUserId());
        
        String statusStr = request.get("status");
        if (statusStr == null) {
            throw new IllegalArgumentException("Status is required");
        }
        
        BookingStatus status = BookingStatus.valueOf(statusStr.toUpperCase());
        Booking updated = bookingService.updateBookingStatus(id, status, context);
        
        return ResponseEntity.ok(updated);
    }
    
    /**
     * Internal service-to-service endpoint — returns only budget fields.
     * Called by the expense service before allowing expense submission.
     * No JWT required; protected by network isolation (internal Docker network only).
     *
     * GET /api/bookings/{id}/budget
     */
    @Operation(
        summary = "Get booking budget (internal)",
        description = "Returns budget and currency for a booking. Used internally by the expense service for submission validation."
    )
    @GetMapping("/{id}/budget")
    public ResponseEntity<Map<String, Object>> getBookingBudget(
            @Parameter(description = "Booking UUID", required = true)
            @PathVariable UUID id) {

        Booking booking = bookingService.getBookingById(id);
        return ResponseEntity.ok(Map.of(
            "id",             booking.getId().toString(),
            "budget",         booking.getBudget(),
            "budgetCurrency", booking.getBudgetCurrency()
        ));
    }

    @Operation(
        summary = "Get booking audit trail",
        description = "Returns all audit records for a booking, ordered by most recent first (ADR-011)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Audit trail retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
    })
    @GetMapping("/{id}/audit")
    public ResponseEntity<List<BookingAudit>> getBookingAudit(
            @Parameter(description = "Booking UUID", required = true)
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, request);
        bookingService.getBooking(id, context);
        List<BookingAudit> trail = bookingAuditService.getAuditTrail(id, context);
        return ResponseEntity.ok(trail);
    }

    @Operation(
        summary = "Delete booking",
        description = "Deletes or cancels a booking. Authorization ensures only authorized users can delete bookings."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Booking deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
        @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBooking(
            @Parameter(description = "Booking UUID", required = true)
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt, httpRequest);
        log.info("Deleting booking {} by user: {}", id, context.getUserId());

        bookingService.deleteBooking(id, context);
        return ResponseEntity.noContent().build();
    }
}
