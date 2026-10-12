package com.corporate.travel.expense.exception;

import java.util.UUID;

/**
 * Thrown when an expense links a travel authorization that does not exist in the
 * expense's tenant. Without the booking there is no budget to check, so submission is refused.
 */
public class LinkedBookingNotFoundException extends RuntimeException {

    public LinkedBookingNotFoundException(UUID bookingId) {
        super("Linked travel authorization " + bookingId + " not found");
    }
}
