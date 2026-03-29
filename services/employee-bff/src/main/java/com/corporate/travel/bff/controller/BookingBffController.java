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
@Tag(name = "Booking BFF", description = "Booking operations with automatic delegation context injection")
public class BookingBffController {

    private final TravelServiceClient travelServiceClient;
    private final DelegationContextService delegationContextService;

    @GetMapping
    @Operation(summary = "List bookings",
        description = "Lists bookings for the current user, or the delegation subject if delegation is active. " +
                      "When delegation is active, X-Delegated-Subject, X-Delegation-Id, and X-Actor-Token " +
                      "headers are threaded to travel-service (ADR-004, ADR-018).")
    public ResponseEntity<JsonNode> getBookings(
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        Optional<DelegationContext> ctx = resolveDelegationContext(session);
        String token = ctx.map(DelegationContext::getDelegationToken).orElse(jwt.getTokenValue());
        return ResponseEntity.ok(travelServiceClient.getBookings(token, ctx));
    }

    @PostMapping
    @Operation(summary = "Create booking",
        description = "Creates a booking. If delegation is active, the audience-scoped delegation token " +
                      "is used (sub=actor) and X-Delegated-Subject identifies the human principal, " +
                      "so the booking is attributed to the subject in audit records (ADR-011).")
    public ResponseEntity<JsonNode> createBooking(
            @RequestBody JsonNode requestBody,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        Optional<DelegationContext> ctx = resolveDelegationContext(session);
        String token = ctx.map(DelegationContext::getDelegationToken).orElse(jwt.getTokenValue());
        return ResponseEntity.ok(travelServiceClient.createBooking(requestBody, token, ctx));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get booking by ID")
    public ResponseEntity<JsonNode> getBooking(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        Optional<DelegationContext> ctx = resolveDelegationContext(session);
        String token = ctx.map(DelegationContext::getDelegationToken).orElse(jwt.getTokenValue());
        return ResponseEntity.ok(travelServiceClient.getBooking(bookingId, token, ctx));
    }

    /**
     * Returns the active, non-expired delegation context from the session if one exists.
     * The full context — not just a token string — is required so that delegation headers
     * (X-Delegated-Subject, X-Delegation-Id, X-Actor-Token) can be threaded on downstream calls.
     */
    private Optional<DelegationContext> resolveDelegationContext(HttpSession session) {
        return delegationContextService.getActiveContext(session);
    }
}
