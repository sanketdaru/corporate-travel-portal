package com.corporate.travel.expense.exception;

/**
 * Exception thrown when an operation is attempted on an expense with invalid status
 */
public class InvalidExpenseStatusException extends RuntimeException {
    
    public InvalidExpenseStatusException(String message) {
        super(message);
    }
}