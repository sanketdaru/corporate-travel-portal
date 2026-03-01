package com.corporate.travel.consent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for Consent Service
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI consentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Consent Service API")
                        .description("Manages consent records with purpose binding for delegated access")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Corporate Travel Platform")
                                .email("api@corporate-travel.com")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token from Keycloak")));
    }
}