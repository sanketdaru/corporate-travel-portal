package com.corporate.travel.bff.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

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

    /**
     * Downstream service answered with an error. Client errors (validation, 403 from OPA, 404) are
     * passed through with their status and body so the caller sees the real cause; server errors
     * become 502, since the BFF itself did not fail.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Object> handleDownstreamError(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.is4xxClientError()) {
            log.warn("Downstream client error {}: {}", status.value(), ex.getResponseBodyAsString());
            MediaType contentType = ex.getResponseHeaders() != null ? ex.getResponseHeaders().getContentType() : null;
            return ResponseEntity.status(status)
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .body(ex.getResponseBodyAsString());
        }
        log.error("Downstream server error {}: {}", status.value(), ex.getResponseBodyAsString());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody(
            HttpStatus.BAD_GATEWAY, "Downstream service error", "Downstream service returned HTTP " + status.value()
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
