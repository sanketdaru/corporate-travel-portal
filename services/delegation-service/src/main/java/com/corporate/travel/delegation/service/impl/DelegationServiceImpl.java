package com.corporate.travel.delegation.service.impl;

import com.corporate.travel.delegation.exception.DelegationNotFoundException;
import com.corporate.travel.delegation.exception.InvalidDelegationException;
import com.corporate.travel.delegation.model.dto.CreateDelegationRequest;
import com.corporate.travel.delegation.model.dto.DelegationChainResponse;
import com.corporate.travel.delegation.model.dto.DelegationResponse;
import com.corporate.travel.delegation.model.entity.Delegation;
import com.corporate.travel.delegation.model.entity.DelegationRelationship;
import com.corporate.travel.delegation.model.entity.UserNode;
import com.corporate.travel.delegation.repository.graph.DelegationGraphRepository;
import com.corporate.travel.delegation.repository.jpa.DelegationRepository;
import com.corporate.travel.delegation.service.DelegationService;
import com.corporate.travel.security.OpaClient;
import com.corporate.travel.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of Delegation Service
 * 
 * This service manages delegation relationships using a dual-database approach:
 * - PostgreSQL: Source of truth for delegation metadata
 * - Neo4j: Optimized for graph traversal queries
 */
@Service
public class DelegationServiceImpl implements DelegationService {

    private static final Logger logger = LoggerFactory.getLogger(DelegationServiceImpl.class);

    private final DelegationRepository delegationRepository;
    private final DelegationGraphRepository delegationGraphRepository;
    private final OpaClient opaClient;

    public DelegationServiceImpl(
            DelegationRepository delegationRepository,
            DelegationGraphRepository delegationGraphRepository,
            OpaClient opaClient) {
        this.delegationRepository = delegationRepository;
        this.delegationGraphRepository = delegationGraphRepository;
        this.opaClient = opaClient;
    }

    @Override
    @Transactional
    public DelegationResponse createDelegation(CreateDelegationRequest request, SecurityContext context) {
        logger.info("Creating delegation: delegator={}, delegate={}, purpose={}", 
            context.getUserId(), request.getDelegateId(), request.getPurpose());

        // 1. Validate request
        validateCreateRequest(request, context);

        // 2. Check OPA authorization
        Map<String, Object> resourceContext = Map.of(
            "resource_type", "delegation",
            "action", "create",
            "delegate_id", request.getDelegateId()
        );

        if (!opaClient.authorize(context, "create_delegation", resourceContext)) {
            throw new AccessDeniedException("Not authorized to create delegation");
        }

        // 3. Check for duplicate active delegation
        if (delegationRepository.existsActiveDelegation(
                context.getTenantId(),
                context.getUserId(),
                request.getDelegateId(),
                LocalDateTime.now())) {
            throw new InvalidDelegationException(
                "Active delegation already exists between these users");
        }

        // 4. Create PostgreSQL entity
        Delegation delegation = Delegation.builder()
            .tenantId(context.getTenantId())
            .delegatorId(context.getUserId())
            .delegateId(request.getDelegateId())
            .purpose(request.getPurpose())
            .scopes(request.getScopes())
            .expiresAt(request.getExpiresAt())
            .active(true)
            .createdBy(context.getUserId())
            .build();

        delegation = delegationRepository.save(delegation);
        logger.info("Delegation created in PostgreSQL: id={}", delegation.getId());

        // 5. Sync to Neo4j asynchronously
        syncToGraph(delegation);

        // 6. Return response
        return toResponse(delegation);
    }

    @Override
    public List<DelegationResponse> getMyDelegations(SecurityContext context) {
        logger.debug("Getting delegations granted by user: {}", context.getUserId());

        // Check OPA authorization
        Map<String, Object> resourceContext = Map.of(
            "resource_type", "delegation",
            "action", "view"
        );

        if (!opaClient.authorize(context, "view_delegations", resourceContext)) {
            throw new AccessDeniedException("Not authorized to view delegations");
        }

        // Query delegations where user is delegator
        List<Delegation> delegations = delegationRepository.findByTenantIdAndDelegatorId(
            context.getTenantId(),
            context.getUserId()
        );

        return delegations.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    public List<DelegationResponse> getDelegationsToMe(SecurityContext context) {
        logger.debug("Getting delegations received by user: {}", context.getUserId());

        // Check OPA authorization
        Map<String, Object> resourceContext = Map.of(
            "resource_type", "delegation",
            "action", "view"
        );

        if (!opaClient.authorize(context, "view_delegations", resourceContext)) {
            throw new AccessDeniedException("Not authorized to view delegations");
        }

        // Query delegations where user is delegate
        List<Delegation> delegations = delegationRepository.findByTenantIdAndDelegateId(
            context.getTenantId(),
            context.getUserId()
        );

        return delegations.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    public DelegationResponse getDelegation(UUID id, SecurityContext context) {
        logger.debug("Getting delegation: id={}", id);

        // Load delegation with tenant check
        Delegation delegation = delegationRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new DelegationNotFoundException(id.toString(), context.getTenantId()));

        // Check OPA authorization
        Map<String, Object> resourceContext = Map.of(
            "resource_type", "delegation",
            "action", "view",
            "delegator_id", delegation.getDelegatorId(),
            "delegate_id", delegation.getDelegateId()
        );

        if (!opaClient.authorize(context, "view_delegation", resourceContext)) {
            throw new AccessDeniedException("Not authorized to view this delegation");
        }

        return toResponse(delegation);
    }

    @Override
    @Transactional
    public void revokeDelegation(UUID id, SecurityContext context) {
        logger.info("Revoking delegation: id={}, user={}", id, context.getUserId());

        // Load delegation with tenant check
        Delegation delegation = delegationRepository.findByIdAndTenantId(id, context.getTenantId())
            .orElseThrow(() -> new DelegationNotFoundException(id.toString(), context.getTenantId()));

        // Check OPA authorization (only delegator can revoke)
        Map<String, Object> resourceContext = Map.of(
            "resource_type", "delegation",
            "action", "revoke",
            "delegator_id", delegation.getDelegatorId()
        );

        if (!opaClient.authorize(context, "revoke_delegation", resourceContext)) {
            throw new AccessDeniedException("Not authorized to revoke this delegation");
        }

        // Revoke in PostgreSQL
        delegation.revoke(context.getUserId());
        delegationRepository.save(delegation);
        logger.info("Delegation revoked in PostgreSQL: id={}", id);

        // Update Neo4j relationship asynchronously
        updateGraphRelationshipStatus(delegation.getId().toString(), false);
    }

    @Override
    public List<DelegationChainResponse> getDelegationChain(String userId, SecurityContext context) {
        logger.debug("Getting delegation chain for user: {}", userId);

        // Check OPA authorization
        Map<String, Object> resourceContext = Map.of(
            "resource_type", "delegation_chain",
            "action", "query",
            "target_user_id", userId
        );

        if (!opaClient.authorize(context, "query_delegation_chain", resourceContext)) {
            throw new AccessDeniedException("Not authorized to query delegation chain");
        }

        // Query Neo4j for delegation chain
        try {
            List<UserNode> chainNodes = delegationGraphRepository.findDelegationChain(
                userId, context.getTenantId()
            );

            // Convert to response DTOs
            return convertChainToResponse(userId, chainNodes);
        } catch (Exception e) {
            logger.warn("Failed to query delegation chain from Neo4j, falling back to PostgreSQL", e);
            // Fallback to PostgreSQL if Neo4j fails
            return buildChainFromPostgresql(userId, context.getTenantId());
        }
    }

    @Override
    public boolean hasDelegation(String delegatorId, String delegateId, SecurityContext context) {
        return delegationRepository.existsActiveDelegation(
            context.getTenantId(),
            delegatorId,
            delegateId,
            LocalDateTime.now()
        );
    }

    /**
     * Validate create delegation request
     */
    private void validateCreateRequest(CreateDelegationRequest request, SecurityContext context) {
        // Cannot delegate to yourself
        if (context.getUserId().equals(request.getDelegateId())) {
            throw new InvalidDelegationException("Cannot delegate to yourself");
        }

        // Validate scopes
        if (request.getScopes() == null || request.getScopes().isEmpty()) {
            throw new InvalidDelegationException("At least one scope is required");
        }

        // Validate expiration
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidDelegationException("Expiration date must be in the future");
        }
    }

    /**
     * Convert Delegation entity to response DTO
     */
    private DelegationResponse toResponse(Delegation delegation) {
        return DelegationResponse.builder()
            .id(delegation.getId())
            .tenantId(delegation.getTenantId())
            .delegatorId(delegation.getDelegatorId())
            .delegateId(delegation.getDelegateId())
            .purpose(delegation.getPurpose())
            .scopes(delegation.getScopes())
            .grantedAt(delegation.getGrantedAt())
            .expiresAt(delegation.getExpiresAt())
            .active(delegation.getActive())
            .revokedAt(delegation.getRevokedAt())
            .revokedBy(delegation.getRevokedBy())
            .valid(delegation.isValid())
            .createdBy(delegation.getCreatedBy())
            .createdAt(delegation.getCreatedAt())
            .build();
    }

    /**
     * Sync delegation to Neo4j graph database (async)
     */
    @Async
    protected void syncToGraph(Delegation delegation) {
        try {
            logger.info("Syncing delegation to Neo4j: id={}", delegation.getId());

            // Find or create delegator node
            UserNode delegator = delegationGraphRepository
                .findByUserIdAndTenantId(delegation.getDelegatorId(), delegation.getTenantId())
                .orElseGet(() -> createUserNode(delegation.getDelegatorId(), delegation.getTenantId()));

            // Find or create delegate node
            UserNode delegate = delegationGraphRepository
                .findByUserIdAndTenantId(delegation.getDelegateId(), delegation.getTenantId())
                .orElseGet(() -> createUserNode(delegation.getDelegateId(), delegation.getTenantId()));

            // Create relationship
            DelegationRelationship relationship = DelegationRelationship.builder()
                .delegationId(delegation.getId().toString())
                .grantedAt(delegation.getGrantedAt())
                .expiresAt(delegation.getExpiresAt())
                .purpose(delegation.getPurpose())
                .scopes(delegation.getScopes())
                .active(delegation.getActive())
                .build();

            delegator.addDelegation(delegate, relationship);
            delegationGraphRepository.save(delegator);

            logger.info("Delegation synced to Neo4j successfully: id={}", delegation.getId());
        } catch (Exception e) {
            // Log error but don't fail the operation - PostgreSQL is source of truth
            logger.error("Failed to sync delegation to Neo4j: id={}", delegation.getId(), e);
        }
    }

    /**
     * Update delegation relationship status in Neo4j (async)
     */
    @Async
    protected void updateGraphRelationshipStatus(String delegationId, boolean active) {
        try {
            delegationGraphRepository.updateDelegationActiveStatus(delegationId, active);
            logger.info("Updated delegation status in Neo4j: id={}, active={}", delegationId, active);
        } catch (Exception e) {
            logger.error("Failed to update delegation status in Neo4j: id={}", delegationId, e);
        }
    }

    /**
     * Create a new user node in Neo4j
     */
    private UserNode createUserNode(String userId, String tenantId) {
        return UserNode.builder()
            .userId(userId)
            .tenantId(tenantId)
            .displayName(userId) // Could be enriched from user service
            .delegations(new HashSet<>())
            .build();
    }

    /**
     * Convert Neo4j chain results to response DTOs
     */
    private List<DelegationChainResponse> convertChainToResponse(String startUserId, List<UserNode> chainNodes) {
        if (chainNodes.isEmpty()) {
            return Collections.emptyList();
        }

        // Build chain response from graph nodes
        List<DelegationChainResponse.DelegationNode> chain = chainNodes.stream()
            .map(node -> DelegationChainResponse.DelegationNode.builder()
                .userId(node.getUserId())
                .displayName(node.getDisplayName())
                .build())
            .collect(Collectors.toList());

        DelegationChainResponse response = DelegationChainResponse.builder()
            .startUserId(startUserId)
            .chain(chain)
            .depth(chain.size())
            .build();

        return List.of(response);
    }

    /**
     * Fallback: Build delegation chain from PostgreSQL
     */
    private List<DelegationChainResponse> buildChainFromPostgresql(String userId, String tenantId) {
        // Simple implementation: get direct delegations only
        List<Delegation> delegations = delegationRepository.findByTenantIdAndDelegatorIdAndActiveTrue(
            tenantId, userId
        );

        if (delegations.isEmpty()) {
            return Collections.emptyList();
        }

        List<DelegationChainResponse.DelegationNode> chain = delegations.stream()
            .map(d -> DelegationChainResponse.DelegationNode.builder()
                .userId(d.getDelegateId())
                .delegationId(d.getId().toString())
                .purpose(d.getPurpose())
                .scopes(d.getScopes())
                .active(d.getActive())
                .build())
            .collect(Collectors.toList());

        DelegationChainResponse response = DelegationChainResponse.builder()
            .startUserId(userId)
            .chain(chain)
            .depth(1) // Only direct delegations
            .build();

        return List.of(response);
    }
}
