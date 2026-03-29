package com.corporate.travel.bff.controller;

import com.corporate.travel.bff.client.TravelServiceClient;
import com.corporate.travel.bff.model.DelegationContext;
import com.corporate.travel.bff.service.DelegationContextService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/bff/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking BFF", description = "Booking operations with automatic delegation token injection")
public class BookingBffController {

    private final TravelServiceClient travelServiceClient;
    private final DelegationContextService delegationContextService;

    @GetMapping
    @Operation(summary = "List bookings",
        description = "Lists bookings for the current user, or the delegation subject if delegation is active.")
    public ResponseEntity<JsonNode> getBookings(
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        String token = resolveToken(jwt, session);
        return ResponseEntity.ok(travelServiceClient.getBookings(token));
    }

    @PostMapping
    @Operation(summary = "Create booking",
        description = "Creates a booking. If delegation is active, uses the delegation token " +
                      "(sub=subject, act.sub=actor) so the booking is made on behalf of the subject.")
    public ResponseEntity<JsonNode> createBooking(
            @RequestBody JsonNode requestBody,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        String token = resolveToken(jwt, session);
        return ResponseEntity.ok(travelServiceClient.createBooking(requestBody, token));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get booking by ID")
    public ResponseEntity<JsonNode> getBooking(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        String token = resolveToken(jwt, session);
        return ResponseEntity.ok(travelServiceClient.getBooking(bookingId, token));
    }

    /**
     * If delegation is active and not expired, use the delegation token.
     * Otherwise fall back to the caller's own token.
     */
    private String resolveToken(Jwt jwt, HttpSession session) {
        Optional<DelegationContext> context = delegationContextService.getActiveContext(session);
        return context.map(DelegationContext::getDelegationToken)
                      .orElse(jwt.getTokenValue());
    }
}
