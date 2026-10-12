package com.corporate.travel.expense.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * RestClient for the travel-service budget call, authenticated as the expense-service
 * service account with a client-credentials token (ADR-009).
 *
 * <p>The token belongs to the service, not to the user submitting the expense: its audience is
 * {@code travel-service} and it carries the {@code budget_reader} realm role. One token is
 * cached for the whole service and renewed when it expires.</p>
 */
@Configuration(proxyBeanMethods = false)
public class TravelServiceClientConfig {

    /** Registration under {@code spring.security.oauth2.client.registration}. */
    public static final String REGISTRATION_ID = "expense-service";

    /** Fixed principal: the cached token is keyed by the service, never by the current user. */
    private static final Authentication SERVICE_PRINCIPAL =
            UsernamePasswordAuthenticationToken.unauthenticated(REGISTRATION_ID, null);

    @Bean
    public OAuth2AuthorizedClientManager serviceAccountAuthorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients,
            ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder) {
        return authorizedClientManager(registrations, authorizedClients, requestFactoryBuilder);
    }

    @Bean
    public RestClient travelServiceRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${travel-service.url:http://travel-service:8081}") String travelServiceUrl,
            OAuth2AuthorizedClientManager serviceAccountAuthorizedClientManager) {
        return restClientBuilder.clone()
                .baseUrl(travelServiceUrl)
                .requestInterceptor(serviceAccountInterceptor(serviceAccountAuthorizedClientManager))
                .build();
    }

    public static OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients,
            ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder) {
        RestClientClientCredentialsTokenResponseClient tokenResponseClient = new RestClientClientCredentialsTokenResponseClient();
        // Same HTTP/1.1 factory as other internal calls (see InternalHttpClientConfig); the token
        // endpoint needs the OAuth2 form/response converters
        tokenResponseClient.setRestClient(RestClient.builder()
                .requestFactory(requestFactoryBuilder.build())
                .configureMessageConverters(converters -> converters
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter())
                        .addCustomConverter(new FormHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(cc -> cc.accessTokenResponseClient(tokenResponseClient))
                .build());
        return manager;
    }

    public static OAuth2ClientHttpRequestInterceptor serviceAccountInterceptor(OAuth2AuthorizedClientManager manager) {
        OAuth2ClientHttpRequestInterceptor interceptor = new OAuth2ClientHttpRequestInterceptor(manager);
        interceptor.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
        interceptor.setPrincipalResolver(request -> SERVICE_PRINCIPAL);
        return interceptor;
    }
}
