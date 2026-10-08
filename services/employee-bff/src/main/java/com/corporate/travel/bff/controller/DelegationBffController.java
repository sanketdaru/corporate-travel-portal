package com.corporate.travel.bff.controller;

import com.corporate.travel.bff.grant.DelegationGrantService;
import com.corporate.travel.bff.model.DelegationContext;
import com.corporate.travel.bff.service.DelegationContextService;
import com.corporate.travel.security.JwtAuthenticationConverter;
import com.corporate.travel.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/bff/delegation")
@RequiredArgsConstructor
@Tag(name = "Delegation BFF", description = "Activate and manage delegation mode")
public class DelegationBffController {

    private final DelegationContextService delegationContextService;
    private final DelegationGrantService delegationGrantService;

    // ── Delegator side: authorize and revoke (ADR-024) ─────────────────────────

    /**
     * Starts the delegator's Keycloak grant for a delegation they created. The browser must
     * navigate to the returned URL; Keycloak shows the delegation consent screen.
     */
    @PostMapping("/{delegationId}/grant")
    @Operation(summary = "Authorize a delegation in Keycloak (delegator)",
        description = "Returns the Keycloak authorization URL for offline_access + delegation:user:<delegate>. " +
                      "After consent, Keycloak redirects to /api/bff/delegation/grant/callback.")
    public ResponseEntity<Map<String, String>> startGrant(
            @PathVariable String delegationId,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        SecurityContext caller = JwtAuthenticationConverter.extractSecurityContext(jwt);
        String url = delegationGrantService.startGrant(delegationId, caller.getUserId(), jwt.getTokenValue(), session);
        return ResponseEntity.ok(Map.of("authorizationUrl", url));
    }

    /**
     * Keycloak redirect target for the grant. Public (no Bearer token — it is a browser
     * navigation); bound to the request that started it by the state held in the BFF session.
     */
    @GetMapping("/grant/callback")
    @Operation(summary = "Keycloak grant callback", description = "Stores the delegator's grant and redirects to the frontend.")
    public ResponseEntity<Void> grantCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpSession session) {

        String next = delegationGrantService.completeGrant(code, state, error, session);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, next).build();
    }

    @GetMapping("/{delegationId}/grant")
    @Operation(summary = "Whether the delegator has authorized this delegation in Keycloak")
    public ResponseEntity<Map<String, Object>> grantStatus(@PathVariable String delegationId) {
        return ResponseEntity.ok(Map.of("delegationId", delegationId,
            "authorized", delegationGrantService.isAuthorized(delegationId)));
    }

    /** Revokes the delegation (delegator only) and its Keycloak grant (offline session). */
    @DeleteMapping("/{delegationId}")
    @Operation(summary = "Revoke a delegation and its Keycloak grant (delegator)")
    public ResponseEntity<Void> revoke(@PathVariable String delegationId, @AuthenticationPrincipal Jwt jwt) {
        delegationGrantService.revoke(delegationId, jwt.getTokenValue());
        return ResponseEntity.noContent().build();
    }

    // ── Delegate side: delegation mode ─────────────────────────────────────────

    /**
     * Activates delegation mode: RFC 8693 delegated exchange of the delegator's granted token
     * with the caller's token as actor_token, one token per downstream audience.
     */
    @PostMapping("/activate/{delegationId}")
    @Operation(summary = "Activate delegation mode",
        description = "Exchanges the delegator's Keycloak grant (may_act) with the caller's token as actor_token, " +
                      "one token per downstream service (delegation.audiences). 409 if the delegator has not " +
                      "authorized the delegation yet. Tokens stay in the server-side session and are not returned.")
    public ResponseEntity<DelegationContext> activateDelegation(
            @PathVariable String delegationId,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        SecurityContext securityContext = JwtAuthenticationConverter.extractSecurityContext(jwt);
        DelegationContext context = delegationContextService.activateDelegation(
            delegationId,
            jwt.getTokenValue(),
            securityContext.getUserId(),
            session);

        return ResponseEntity.ok(context);
    }

    /**
     * Deactivates delegation mode and clears the session context.
     */
    @DeleteMapping("/deactivate")
    @Operation(summary = "Deactivate delegation mode")
    public ResponseEntity<Void> deactivateDelegation(HttpSession session) {
        delegationContextService.deactivateDelegation(session);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the currently active delegation context, if any.
     */
    @GetMapping("/context")
    @Operation(summary = "Get active delegation context")
    public ResponseEntity<Map<String, Object>> getDelegationContext(HttpSession session) {
        Optional<DelegationContext> context = delegationContextService.getActiveContext(session);
        if (context.isEmpty()) {
            return ResponseEntity.ok(Map.of("delegationActive", false));
        }
        DelegationContext ctx = context.get();
        return ResponseEntity.ok(Map.of(
            "delegationActive", true,
            "delegationId", ctx.getDelegationId(),
            "actorId", ctx.getActorId(),
            "subjectId", ctx.getSubjectId(),
            "audiences", ctx.getAudiences(),
            "expiresAt", ctx.getExpiresAt().toString()
        ));
    }
}
