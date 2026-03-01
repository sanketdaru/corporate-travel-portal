package com.corporate.travel.delegation.exception;

/**
 * Exception thrown when a delegation is not found
 */
public class DelegationNotFoundException extends RuntimeException {
    
    public DelegationNotFoundException(String message) {
        super(message);
    }
    
    public DelegationNotFoundException(String delegationId, String tenantId) {
        super(String.format("Delegation with ID %s not found in tenant %s", delegationId, tenantId));
    }
}