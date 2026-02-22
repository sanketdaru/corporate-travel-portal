package com.corporate.travel.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback Controller
 * 
 * Handles circuit breaker fallback responses when backend services are unavailable.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/travel")
    public ResponseEntity<Map<String, Object>> travelServiceFallback() {
        log.warn("Travel service circuit breaker triggered - returning fallback response");
        return buildFallbackResponse("Travel Service");
    }

    @GetMapping("/expenses")
    public ResponseEntity<Map<String, Object>> expenseServiceFallback() {
        log.warn("Expense service circuit breaker triggered - returning fallback response");
        return buildFallbackResponse("Expense Service");
    }

    @GetMapping("/approvals")
    public ResponseEntity<Map<String, Object>> approvalServiceFallback() {
        log.warn("Approval service circuit breaker triggered - returning fallback response");
        return buildFallbackResponse("Approval Service");
    }

    @GetMapping("/delegations")
    public ResponseEntity<Map<String, Object>> delegationServiceFallback() {
        log.warn("Delegation service circuit breaker triggered - returning fallback response");
        return buildFallbackResponse("Delegation Service");
    }

    @GetMapping("/consent")
    public ResponseEntity<Map<String, Object>> consentServiceFallback() {
        log.warn("Consent service circuit breaker triggered - returning fallback response");
        return buildFallbackResponse("Consent Service");
    }

    private ResponseEntity<Map<String, Object>> buildFallbackResponse(String serviceName) {
        Map<String, Object> response = Map.of(
            "error", "Service Unavailable",
            "message", serviceName + " is currently unavailable. Please try again later.",
            "timestamp", Instant.now().toString(),
            "status", HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(response);
    }
}