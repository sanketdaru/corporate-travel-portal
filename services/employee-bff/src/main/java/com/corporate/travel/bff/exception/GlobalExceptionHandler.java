package com.corporate.travel.bff.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TokenExchangeException.class)
    public ResponseEntity<Map<String, Object>> handleTokenExchangeException(TokenExchangeException ex) {
        log.error("Token exchange failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody(
            HttpStatus.BAD_GATEWAY, "Token exchange failed", ex.getMessage()
        ));
    }

    @ExceptionHandler(DelegationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDelegationNotFoundException(DelegationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
            HttpStatus.NOT_FOUND, "Delegation not found", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(
            HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred"
        ));
    }

    private Map<String, Object> errorBody(HttpStatus status, String error, String message) {
        return Map.of(
            "timestamp", Instant.now().toString(),
            "status", status.value(),
            "error", error,
            "message", message
        );
    }
}
