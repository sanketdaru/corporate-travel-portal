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
 *
 * All services expose routes under their own /api/<resource> prefix, so no
 * path stripping is needed — the full path is forwarded as-is (stripPrefix(0)).
 */
@Slf4j
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        log.info("Configuring API Gateway routes");

        return builder.routes()
            // Booking (Travel) Service — /api/bookings/**
            .route("travel-service-bookings", r -> r
                .path("/api/bookings/**")
                .uri("http://travel-service:8081")
            )

            // Travel Service OpenAPI Docs - Root path
            .route("travel-service-docs-root", r -> r
                .path("/travel-service-docs")
                .filters(f -> f.rewritePath("/travel-service-docs", "/api-docs"))
                .uri("http://travel-service:8081")
            )

            // Travel Service OpenAPI Docs - With segments
            .route("travel-service-docs", r -> r
                .path("/travel-service-docs/**")
                .filters(f -> f.rewritePath("/travel-service-docs/(?<segment>.*)", "/api-docs/$\\{segment}"))
                .uri("http://travel-service:8081")
            )

            // Expense Service — /api/expenses/**
            .route("expense-service-api", r -> r
                .path("/api/expenses/**")
                .uri("http://expense-service:8082")
            )

            // Expense Service OpenAPI Docs - Root path
            .route("expense-service-docs-root", r -> r
                .path("/expense-service-docs")
                .filters(f -> f.rewritePath("/expense-service-docs", "/api-docs"))
                .uri("http://expense-service:8082")
            )

            // Expense Service OpenAPI Docs - With segments
            .route("expense-service-docs", r -> r
                .path("/expense-service-docs/**")
                .filters(f -> f.rewritePath("/expense-service-docs/(?<segment>.*)", "/api-docs/$\\{segment}"))
                .uri("http://expense-service:8082")
            )

            // Delegation Service — /api/delegations/**
            .route("delegation-service-api", r -> r
                .path("/api/delegations/**")
                .uri("http://delegation-service:8083")
            )

            // Consent Service — /api/consents/**
            .route("consent-service-api", r -> r
                .path("/api/consents/**")
                .uri("http://consent-service:8084")
            )

            .build();
    }
}
