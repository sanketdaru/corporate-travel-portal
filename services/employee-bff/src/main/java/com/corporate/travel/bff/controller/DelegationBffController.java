package com.corporate.travel.bff.controller;

import com.corporate.travel.bff.model.DelegationContext;
import com.corporate.travel.bff.service.DelegationContextService;
import com.corporate.travel.security.JwtAuthenticationConverter;
import com.corporate.travel.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
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

    /**
     * Activates delegation mode. Triggers Standard Token Exchange V2:
     * the caller's JWT is used as subject_token to establish chain of trust.
     */
    @PostMapping("/activate/{delegationId}")
    @Operation(summary = "Activate delegation mode",
        description = "Performs Standard Token Exchange V2 to obtain a delegation token. " +
                      "The caller's token is the mandatory subject_token (chain of trust).")
    public ResponseEntity<DelegationContext> activateDelegation(
            @PathVariable String delegationId,
            @RequestParam(defaultValue = "travel-service") String audience,
            @AuthenticationPrincipal Jwt jwt,
            HttpSession session) {

        SecurityContext securityContext = JwtAuthenticationConverter.extractSecurityContext(jwt);
        DelegationContext context = delegationContextService.activateDelegation(
            delegationId,
            jwt.getTokenValue(),
            securityContext.getUserId(),
            audience,
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
            "audience", ctx.getAudience(),
            "expiresAt", ctx.getExpiresAt().toString()
        ));
    }
}
