package com.corporate.travel.consent.exception;

/**
 * Exception thrown when consent validation fails
 */
public class InvalidConsentException extends RuntimeException {
    
    public InvalidConsentException(String message) {
        super(message);
    }
}