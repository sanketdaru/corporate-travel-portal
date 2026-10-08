package com.corporate.travel.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * One RestClient per downstream service, each cloned from Boot's auto-configured builder so
 * they share message converters (Jackson 3) and observation settings.
 */
@Configuration
public class RestClientConfig {

    private final BffProperties properties;
    private final RestClient.Builder builder;

    public RestClientConfig(BffProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.builder = builder;
    }

    @Bean("travelServiceRestClient")
    public RestClient travelServiceRestClient() {
        return builder.clone().baseUrl(properties.getServices().getTravelServiceUrl()).build();
    }

    @Bean("expenseServiceRestClient")
    public RestClient expenseServiceRestClient() {
        return builder.clone().baseUrl(properties.getServices().getExpenseServiceUrl()).build();
    }

    @Bean("delegationServiceRestClient")
    public RestClient delegationServiceRestClient() {
        return builder.clone().baseUrl(properties.getServices().getDelegationServiceUrl()).build();
    }

    @Bean("consentServiceRestClient")
    public RestClient consentServiceRestClient() {
        return builder.clone().baseUrl(properties.getServices().getConsentServiceUrl()).build();
    }
}
