package com.corporate.travel.travel.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger Configuration
 * 
 * Provides API documentation and Swagger UI for the Travel Service
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public OpenAPI travelServiceOpenAPI() {
        // Security scheme for JWT Bearer token
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT token obtained from Keycloak. Use the format: Bearer <token>");

        // Security requirement
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");

        return new OpenAPI()
                .info(new Info()
                        .title("Travel Service API")
                        .version("1.0.0")
                        .description("""
                                Corporate Travel & Expense Platform - Travel Service
                                
                                This service manages travel bookings including flights, hotels, and car rentals.
                                
                                ## Authentication
                                All endpoints require JWT authentication. Obtain a token from Keycloak:
                                ```bash
                                ./scripts/get-token.sh alice.employee
                                ```
                                
                                ## Authorization
                                Authorization is handled by Open Policy Agent (OPA) with support for:
                                - Multi-tenant isolation
                                - Role-based access control
                                - Delegation (acting on behalf of others)
                                - Consent validation
                                
                                ## Key Features
                                - Create and manage travel bookings
                                - Multi-tenant data isolation
                                - Delegated booking support
                                - Status workflow management
                                - Comprehensive audit trail
                                """)
                        .contact(new Contact()
                                .name("Corporate Travel Platform")
                                .url("https://github.com/corporate-travel/platform")
                                .email("support@corporate-travel.example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8081")
                                .description("Local Development Server"),
                        new Server()
                                .url("http://travel-service:8081")
                                .description("Docker Environment")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme))
                .addSecurityItem(securityRequirement);
    }
}
