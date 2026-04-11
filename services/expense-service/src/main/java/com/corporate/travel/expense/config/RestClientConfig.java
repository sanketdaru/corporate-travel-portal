package com.corporate.travel.expense.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client configuration for internal service-to-service calls.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
