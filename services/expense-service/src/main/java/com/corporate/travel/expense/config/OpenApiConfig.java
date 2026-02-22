package com.corporate.travel.expense.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for Expense Service
 */
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI expenseServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Expense Service API")
                .description("Corporate Travel & Expense Platform - Expense Management Service\n\n" +
                    "Manages expense reports and expense items with multi-tenant isolation and approval workflows.")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Corporate Travel Platform")
                    .url("https://github.com/corporate-travel-portal"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8000/api/expenses")
                    .description("API Gateway (Local)")
            ))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT token from Keycloak")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
