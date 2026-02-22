package com.corporate.travel.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway Route Configuration
 *
 * Defines routes to backend microservices with path-based routing.
 * Routes are configured programmatically for better type safety and IDE support.
 */
@Slf4j
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        log.info("Configuring API Gateway routes");

        return builder.routes()
            // Travel Service Routes
            .route("travel-service", r -> r
                .path("/api/travel/**")
                .filters(f -> f.stripPrefix(2))  // Remove /api/travel prefix
                .uri("http://travel-service:8081")
            )

            // Expense Service Routes
            .route("expense-service", r -> r
                .path("/api/expenses/**")
                .filters(f -> f.stripPrefix(2))  // Remove /api/expenses prefix
                .uri("http://expense-service:8082")
            )

            // Future routes for approval-service, delegation-service, consent-service
            // can be added here when those services are implemented

            .build();
    }
}