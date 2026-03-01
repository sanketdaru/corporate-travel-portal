package com.corporate.travel.delegation.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler for Delegation Service
 * 
 * Handles exceptions and converts them to RFC 7807 Problem Details
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DelegationNotFoundException.class)
    public ProblemDetail handleDelegationNotFound(DelegationNotFoundException ex) {
        logger.warn("Delegation not found: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problemDetail.setTitle("Delegation Not Found");
        problemDetail.setType(URI.create("https://corporate-travel.com/errors/delegation-not-found"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(InvalidDelegationException.class)
    public ProblemDetail handleInvalidDelegation(InvalidDelegationException ex) {
        logger.warn("Invalid delegation: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );
        problemDetail.setTitle("Invalid Delegation");
        problemDetail.setType(URI.create("https://corporate-travel.com/errors/invalid-delegation"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        logger.warn("Access denied: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            "Not authorized to perform this operation"
        );
        problemDetail.setTitle("Access Denied");
        problemDetail.setType(URI.create("https://corporate-travel.com/errors/access-denied"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        logger.warn("Validation failed: {}", errors);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed: " + errors
        );
        problemDetail.setTitle("Validation Error");
        problemDetail.setType(URI.create("https://corporate-travel.com/errors/validation-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        logger.error("Unexpected error occurred", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later."
        );
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://corporate-travel.com/errors/internal-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
