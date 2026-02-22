package com.corporate.travel.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application
 * 
 * Provides centralized routing, JWT validation, and security enforcement
 * for all backend microservices in the Corporate Travel & Expense Platform.
 * 
 * Key Responsibilities:
 * - JWT token validation (single point of authentication)
 * - Request routing to backend services
 * - CORS configuration
 * - Security headers
 * - Request/response logging
 * - Circuit breaker patterns
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}