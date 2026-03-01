package com.corporate.travel.consent.exception;

/**
 * Exception thrown when access is denied by OPA
 */
public class AccessDeniedException extends RuntimeException {
    
    public AccessDeniedException(String message) {
        super(message);
    }
}