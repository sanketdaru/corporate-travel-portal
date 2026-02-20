package com.corporate.travel.expense.exception;

import java.util.UUID;

/**
 * Exception thrown when an expense item is not found
 */
public class ExpenseItemNotFoundException extends RuntimeException {
    
    public ExpenseItemNotFoundException(UUID id) {
        super("Expense item not found with ID: " + id);
    }
    
    public ExpenseItemNotFoundException(String message) {
        super(message);
    }
}