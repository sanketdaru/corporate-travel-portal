package com.corporate.travel.expense.exception;

import java.util.UUID;

/**
 * Exception thrown when an expense is not found
 */
public class ExpenseNotFoundException extends RuntimeException {
    
    public ExpenseNotFoundException(UUID id) {
        super("Expense not found with ID: " + id);
    }
    
    public ExpenseNotFoundException(String message) {
        super(message);
    }
}