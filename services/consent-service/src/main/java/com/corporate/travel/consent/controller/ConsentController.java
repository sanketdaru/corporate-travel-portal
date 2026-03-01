package com.corporate.travel.consent.controller;

import com.corporate.travel.consent.model.dto.*;
import com.corporate.travel.consent.service.ConsentService;
import com.corporate.travel.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for consent management
 */
@RestController
@RequestMapping("/api/consents")
@Tag(name = "Consent Management", description = "APIs for managing consent records with purpose binding")
public class ConsentController {

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @PostMapping
    @Operation(summary = "Grant a new consent", description = "Create a consent record for delegated access")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Consent granted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid consent data"),
        @ApiResponse(responseCode = "403", description = "Not authorized to grant consent"),
        @ApiResponse(responseCode = "409", description = "Duplicate active consent exists")
    })
    public ResponseEntity<ConsentResponse> grantConsent(
            @Valid @RequestBody CreateConsentRequest request,
            Authentication authentication) {
        SecurityContext context = buildSecurityContext(authentication);
        ConsentResponse response = consentService.grantConsent(request, context);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get consent by ID", description = "Retrieve consent details by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Consent found"),
        @ApiResponse(responseCode = "404", description = "Consent not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view this consent")
    })
    public ResponseEntity<ConsentResponse> getConsent(
            @PathVariable UUID id,
            Authentication authentication) {
        SecurityContext context = buildSecurityContext(authentication);
        ConsentResponse response = consentService.getConsent(id, context);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-consents")
    @Operation(summary = "List my consents", description = "List all consents granted by the current user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Consents retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<List<ConsentResponse>> getMyConsents(Authentication authentication) {
        SecurityContext context = buildSecurityContext(authentication);
        List<ConsentResponse> responses = consentService.getMyConsents(context);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/to-me")
    @Operation(summary = "List consents granted to me", description = "List all consents granted to the current user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Consents retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<List<ConsentResponse>> getConsentsToMe(Authentication authentication) {
        SecurityContext context = buildSecurityContext(authentication);
        List<ConsentResponse> responses = consentService.getConsentsToMe(context);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke consent", description = "Revoke an existing consent")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Consent revoked successfully"),
        @ApiResponse(responseCode = "404", description = "Consent not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized to revoke this consent")
    })
    public ResponseEntity<Void> revokeConsent(
            @PathVariable UUID id,
            Authentication authentication) {
        SecurityContext context = buildSecurityContext(authentication);
        consentService.revokeConsent(id, context);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate consent", description = "Check if valid consent exists for specific action/scopes")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation result returned"),
        @ApiResponse(responseCode = "400", description = "Invalid validation request"),
        @ApiResponse(responseCode = "403", description = "Not authorized to validate consent")
    })
    public ResponseEntity<ValidateConsentResponse> validateConsent(
            @Valid @RequestBody ValidateConsentRequest request,
            Authentication authentication) {
        SecurityContext context = buildSecurityContext(authentication);
        ValidateConsentResponse response = consentService.validateConsent(request, context);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Get consent audit trail", description = "Retrieve audit trail for a consent")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Audit trail retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Consent not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view audit trail")
    })
    public ResponseEntity<List<ConsentAuditResponse>> getConsentAuditTrail(
            @PathVariable UUID id,
            Authentication authentication) {
        SecurityContext context = buildSecurityContext(authentication);
        List<ConsentAuditResponse> responses = consentService.getConsentAuditTrail(id, context);
        return ResponseEntity.ok(responses);
    }

    private SecurityContext buildSecurityContext(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        
        String userId = jwt.getClaimAsString("sub");
        String tenantId = jwt.getClaimAsString("tenant_id");
        String actorSub = jwt.getClaimAsString("act") != null ? 
            jwt.getClaimAsString("act") : userId;
        String subjectId = jwt.getClaimAsString("act") != null ? userId : null;
        
        return SecurityContext.builder()
                .userId(userId)
                .tenantId(tenantId)
                .actorId(actorSub)
                .subjectId(subjectId)
                .build();
    }
}