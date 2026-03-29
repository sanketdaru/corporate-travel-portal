package com.corporate.travel.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private final BffProperties properties;

    public WebClientConfig(BffProperties properties) {
        this.properties = properties;
    }

    @Bean("travelServiceWebClient")
    public WebClient travelServiceWebClient() {
        return WebClient.builder()
            .baseUrl(properties.getServices().getTravelServiceUrl())
            .build();
    }

    @Bean("expenseServiceWebClient")
    public WebClient expenseServiceWebClient() {
        return WebClient.builder()
            .baseUrl(properties.getServices().getExpenseServiceUrl())
            .build();
    }

    @Bean("delegationServiceWebClient")
    public WebClient delegationServiceWebClient() {
        return WebClient.builder()
            .baseUrl(properties.getServices().getDelegationServiceUrl())
            .build();
    }

    @Bean("consentServiceWebClient")
    public WebClient consentServiceWebClient() {
        return WebClient.builder()
            .baseUrl(properties.getServices().getConsentServiceUrl())
            .build();
    }

    @Bean("keycloakWebClient")
    public WebClient keycloakWebClient() {
        return WebClient.builder()
            .baseUrl(properties.getKeycloak().getUrl())
            .build();
    }
}
