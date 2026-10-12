package com.corporate.travel.expense.exception;

import java.util.UUID;

/**
 * Thrown when the budget of a linked travel authorization cannot be read from the
 * travel service. Submission is refused rather than skipping the budget check.
 */
public class TravelServiceUnavailableException extends RuntimeException {

    public TravelServiceUnavailableException(UUID bookingId, Throwable cause) {
        super("Cannot verify the travel budget for booking " + bookingId + "; try again later", cause);
    }
}
