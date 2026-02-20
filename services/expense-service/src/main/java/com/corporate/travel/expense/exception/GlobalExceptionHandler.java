package com.corporate.travel.expense.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler for the Expense Service
 * Returns RFC 7807 Problem Details for HTTP APIs
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ExpenseNotFoundException.class)
    public ProblemDetail handleExpenseNotFound(ExpenseNotFoundException ex) {
        log.warn("Expense not found: {}", ex.getMessage());
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problemDetail.setTitle("Expense Not Found");
        problemDetail.setType(URI.create("https://api.corporate-travel.com/errors/expense-not-found"));
        problemDetail.setProperty("timestamp", Instant.now());
        
        return problemDetail;
    }
    
    @ExceptionHandler(ExpenseItemNotFoundException.class)
    public ProblemDetail handleExpenseItemNotFound(ExpenseItemNotFoundException ex) {
        log.warn("Expense item not found: {}", ex.getMessage());
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problemDetail.setTitle("Expense Item Not Found");
        problemDetail.setType(URI.create("https://api.corporate-travel.com/errors/expense-item-not-found"));
        problemDetail.setProperty("timestamp", Instant.now());
        
        return problemDetail;
    }
    
    @ExceptionHandler(InvalidExpenseStatusException.class)
    public ProblemDetail handleInvalidExpenseStatus(InvalidExpenseStatusException ex) {
        log.warn("Invalid expense status: {}", ex.getMessage());
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );
        problemDetail.setTitle("Invalid Expense Status");
        problemDetail.setType(URI.create("https://api.corporate-travel.com/errors/invalid-expense-status"));
        problemDetail.setProperty("timestamp", Instant.now());
        
        return problemDetail;
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            ex.getMessage()
        );
        problemDetail.setTitle("Access Denied");
        problemDetail.setType(URI.create("https://api.corporate-travel.com/errors/access-denied"));
        problemDetail.setProperty("timestamp", Instant.now());
        
        return problemDetail;
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );
        problemDetail.setTitle("Invalid Request");
        problemDetail.setType(URI.create("https://api.corporate-travel.com/errors/invalid-request"));
        problemDetail.setProperty("timestamp", Instant.now());
        
        return problemDetail;
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.warn("Invalid state: {}", ex.getMessage());
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage()
        );
        problemDetail.setTitle("Invalid State");
        problemDetail.setType(URI.create("https://api.corporate-travel.com/errors/invalid-state"));
        problemDetail.setProperty("timestamp", Instant.now());
        
        return problemDetail;
    }
    
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
        );
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://api.corporate-travel.com/errors/internal-server-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        
        return problemDetail;
    }
}
