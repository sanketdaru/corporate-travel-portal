package com.corporate.travel.delegation.exception;

/**
 * Exception thrown when a delegation operation is invalid
 */
public class InvalidDelegationException extends RuntimeException {
    
    public InvalidDelegationException(String message) {
        super(message);
    }
}