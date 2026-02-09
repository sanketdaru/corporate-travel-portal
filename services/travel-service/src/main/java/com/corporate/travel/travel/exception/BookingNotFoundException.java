package com.corporate.travel.travel.exception;

import java.util.UUID;

/**
 * Exception thrown when a booking is not found
 */
public class BookingNotFoundException extends RuntimeException {
    
    public BookingNotFoundException(UUID id) {
        super("Booking not found with id: " + id);
    }
    
    public BookingNotFoundException(String message) {
        super(message);
    }
}
