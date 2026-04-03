package com.corporate.travel.delegation.controller;

import com.corporate.travel.delegation.model.dto.CreateDelegationRequest;
import com.corporate.travel.delegation.model.dto.DelegationChainResponse;
import com.corporate.travel.delegation.model.dto.DelegationResponse;
import com.corporate.travel.delegation.service.DelegationService;
import com.corporate.travel.security.JwtAuthenticationConverter;
import com.corporate.travel.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Delegation Management
 *
 * Provides endpoints for creating, querying, and revoking delegations
 */
@RestController
@RequestMapping("/api/delegations")
@Tag(name = "Delegation Management", description = "APIs for managing delegation relationships")
@SecurityRequirement(name = "bearer-jwt")
public class DelegationController {

    private static final Logger logger = LoggerFactory.getLogger(DelegationController.class);

    private final DelegationService delegationService;

    public DelegationController(DelegationService delegationService) {
        this.delegationService = delegationService;
    }

    @PostMapping
    @Operation(
        summary = "Create a new delegation",
        description = "Grant delegation permission to another user to act on your behalf"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Delegation created successfully",
            content = @Content(schema = @Schema(implementation = DelegationResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "403", description = "Not authorized"),
        @ApiResponse(responseCode = "409", description = "Delegation already exists")
    })
    public ResponseEntity<DelegationResponse> createDelegation(
            @Valid @RequestBody CreateDelegationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        SecurityContext securityContext = JwtAuthenticationConverter.extractSecurityContext(jwt);
        logger.info("POST /api/delegations - Creating delegation for user: {}", securityContext.getUserId());
        DelegationResponse response = delegationService.createDelegation(request, securityContext);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-delegations")
    @Operation(
        summary = "Get delegations granted by me",
        description = "Retrieve all delegations where the current user is the delegator"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Delegations retrieved successfully",
            content = @Content(schema = @Schema(implementation = DelegationResponse.class))
        ),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<List<DelegationResponse>> getMyDelegations(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        SecurityContext securityContext = JwtAuthenticationConverter.extractSecurityContext(jwt);
        logger.info("GET /api/delegations/my-delegations - User: {}", securityContext.getUserId());
        List<DelegationResponse> delegations = delegationService.getMyDelegations(securityContext);
        return ResponseEntity.ok(delegations);
    }

    @GetMapping("/to-me")
    @Operation(
        summary = "Get delegations granted to me",
        description = "Retrieve all delegations where the current user is the delegate"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Delegations retrieved successfully",
            content = @Content(schema = @Schema(implementation = DelegationResponse.class))
        ),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<List<DelegationResponse>> getDelegationsToMe(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        SecurityContext securityContext = JwtAuthenticationConverter.extractSecurityContext(jwt);
        logger.info("GET /api/delegations/to-me - User: {}", securityContext.getUserId());
        List<DelegationResponse> delegations = delegationService.getDelegationsToMe(securityContext);
        return ResponseEntity.ok(delegations);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get delegation by ID",
        description = "Retrieve a specific delegation's details"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Delegation found",
            content = @Content(schema = @Schema(implementation = DelegationResponse.class))
        ),
        @ApiResponse(responseCode = "403", description = "Not authorized"),
        @ApiResponse(responseCode = "404", description = "Delegation not found")
    })
    public ResponseEntity<DelegationResponse> getDelegation(
            @Parameter(description = "Delegation ID") @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        SecurityContext securityContext = JwtAuthenticationConverter.extractSecurityContext(jwt);
        logger.info("GET /api/delegations/{} - User: {}", id, securityContext.getUserId());
        DelegationResponse response = delegationService.getDelegation(id, securityContext);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Revoke a delegation",
        description = "Revoke an existing delegation. Only the delegator can revoke."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Delegation revoked successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to revoke"),
        @ApiResponse(responseCode = "404", description = "Delegation not found")
    })
    public ResponseEntity<Void> revokeDelegation(
            @Parameter(description = "Delegation ID") @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        SecurityContext securityContext = JwtAuthenticationConverter.extractSecurityContext(jwt);
        logger.info("DELETE /api/delegations/{} - User: {}", id, securityContext.getUserId());
        delegationService.revokeDelegation(id, securityContext);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/chain")
    @Operation(
        summary = "Get delegation chain",
        description = "Retrieve the delegation chain for a user (graph traversal). " +
                     "Shows all users a delegate can transitively act as."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Delegation chain retrieved",
            content = @Content(schema = @Schema(implementation = DelegationChainResponse.class))
        ),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<List<DelegationChainResponse>> getDelegationChain(
            @Parameter(description = "User ID to query chain for") @RequestParam String userId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        SecurityContext securityContext = JwtAuthenticationConverter.extractSecurityContext(jwt);
        logger.info("GET /api/delegations/chain?userId={} - Requester: {}", userId, securityContext.getUserId());
        List<DelegationChainResponse> chain = delegationService.getDelegationChain(userId, securityContext);
        return ResponseEntity.ok(chain);
    }

    @GetMapping("/check")
    @Operation(
        summary = "Check if delegation exists",
        description = "Check if an active delegation exists between two users"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Check completed"),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<Boolean> checkDelegation(
            @Parameter(description = "Delegator user ID") @RequestParam String delegatorId,
            @Parameter(description = "Delegate user ID") @RequestParam String delegateId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        SecurityContext securityContext = JwtAuthenticationConverter.extractSecurityContext(jwt);
        logger.info("GET /api/delegations/check - delegator={}, delegate={}", delegatorId, delegateId);
        boolean exists = delegationService.hasDelegation(delegatorId, delegateId, securityContext);
        return ResponseEntity.ok(exists);
    }
}
