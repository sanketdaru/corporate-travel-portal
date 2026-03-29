package com.corporate.travel.bff.controller;

import com.corporate.travel.bff.model.DelegationContext;
import com.corporate.travel.bff.service.ApiAggregationService;
import com.corporate.travel.bff.service.DelegationContextService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/bff/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard BFF", description = "Aggregated dashboard combining bookings and expenses (ADR-018)")
public class DashboardBffController {

    private final ApiAggregationService apiAggregationService;
    private final DelegationContextService delegationContextService;

    @GetMapping
    @Operation(summary = "Aggregated dashboard",
        description = "Returns bookings and expenses in a single response. When delegation is active, " +
                      "both downstream calls carry the full delegation context headers " +
                      "(X-Delegated-Subject, X-Delegation-Id, X-Actor-Token) so data is scoped " +
                      "to the delegation subject and fully auditable (ADR-004, ADR-011, ADR-018).")
    public ResponseEntity<JsonNode> getDashboard(
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        Optional<DelegationContext> ctx = delegationContextService.getActiveContext(session);
        String token = ctx.map(DelegationContext::getDelegationToken).orElse(jwt.getTokenValue());
        return ResponseEntity.ok(apiAggregationService.getDashboard(token, ctx));
    }
}
