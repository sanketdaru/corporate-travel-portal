package com.corporate.travel.delegation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger Configuration
 * 
 * Provides API documentation at /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI delegationServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Delegation Service API")
                .description("Manages delegation relationships using PostgreSQL and Neo4j graph database. " +
                    "Provides CRUD operations for delegations and graph traversal for delegation chains.")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Corporate Travel Platform")
                    .email("support@corporate-travel.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
            .components(new Components()
                .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .in(SecurityScheme.In.HEADER)
                    .name("Authorization")));
    }
}