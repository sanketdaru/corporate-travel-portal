package com.corporate.travel.delegation.service;

import com.corporate.travel.delegation.model.dto.CreateDelegationRequest;
import com.corporate.travel.delegation.model.dto.DelegationChainResponse;
import com.corporate.travel.delegation.model.dto.DelegationResponse;
import com.corporate.travel.security.SecurityContext;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Delegation operations
 */
public interface DelegationService {

    /**
     * Create a new delegation
     * @param request Delegation details
     * @param context Security context of the user creating the delegation
     * @return Created delegation
     */
    DelegationResponse createDelegation(CreateDelegationRequest request, SecurityContext context);

    /**
     * Get all delegations granted by the current user (where user is delegator)
     * @param context Security context
     * @return List of delegations
     */
    List<DelegationResponse> getMyDelegations(SecurityContext context);

    /**
     * Get all delegations granted to the current user (where user is delegate)
     * @param context Security context
     * @return List of delegations
     */
    List<DelegationResponse> getDelegationsToMe(SecurityContext context);

    /**
     * Get a specific delegation by ID
     * @param id Delegation ID
     * @param context Security context
     * @return Delegation details
     */
    DelegationResponse getDelegation(UUID id, SecurityContext context);

    /**
     * Revoke a delegation
     * @param id Delegation ID
     * @param context Security context
     */
    void revokeDelegation(UUID id, SecurityContext context);

    /**
     * Get delegation chain for a user (graph traversal)
     * @param userId User ID to query
     * @param context Security context
     * @return Delegation chain
     */
    List<DelegationChainResponse> getDelegationChain(String userId, SecurityContext context);

    /**
     * Check if an active delegation exists between two users
     * @param delegatorId Delegator user ID
     * @param delegateId Delegate user ID
     * @param context Security context
     * @return true if delegation exists
     */
    boolean hasDelegation(String delegatorId, String delegateId, SecurityContext context);
}