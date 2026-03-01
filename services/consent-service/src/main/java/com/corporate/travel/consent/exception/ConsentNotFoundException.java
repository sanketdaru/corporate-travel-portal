package com.corporate.travel.consent.exception;

import java.util.UUID;

/**
 * Exception thrown when a consent is not found
 */
public class ConsentNotFoundException extends RuntimeException {
    
    public ConsentNotFoundException(UUID id) {
        super(String.format("Consent not found with id: %s", id));
    }

    public ConsentNotFoundException(String message) {
        super(message);
    }
}