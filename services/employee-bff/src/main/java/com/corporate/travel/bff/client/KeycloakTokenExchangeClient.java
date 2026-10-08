package com.corporate.travel.bff.client;

import com.corporate.travel.bff.config.BffProperties;
import com.corporate.travel.bff.exception.TokenExchangeException;
import com.corporate.travel.bff.model.TokenExchangeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Performs RFC 8693 delegation token exchange against Keycloak (ADR-024), using Spring
 * Security's {@link RestClientTokenExchangeTokenResponseClient}.
 *
 * <p>Security contract:</p>
 * <ul>
 *   <li>{@code subject_token} — the delegator's access token, carrying {@code may_act} for the actor
 *       (obtained from the delegator's stored grant). The issued token's {@code sub} is the delegator.</li>
 *   <li>{@code actor_token} — the actor's own access token. Keycloak checks it matches
 *       {@code may_act.sub} and records the actor in the issued token's {@code act} claim.</li>
 *   <li>{@code audience} scopes the issued token to a single resource server, preventing replay.</li>
 *   <li>Both tokens are sent as {@code ...:token-type:access_token}, the only type Keycloak accepts.</li>
 * </ul>
 *
 * <p>The client is stateless on purpose: Spring's {@code OAuth2AuthorizedClientManager} caches
 * tokens per principal, which would hand an actor the token from a previous delegation.</p>
 */
@Component
@Slf4j
public class KeycloakTokenExchangeClient {

    static final String REGISTRATION_ID = "keycloak-token-exchange";

    private final ClientRegistration registration;
    private final RestClientTokenExchangeTokenResponseClient tokenResponseClient;

    public KeycloakTokenExchangeClient(BffProperties properties, ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder) {
        BffProperties.Keycloak keycloak = properties.getKeycloak();
        this.registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId(keycloak.getClientId())
            .clientSecret(keycloak.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
            .tokenUri(keycloak.getUrl() + "/realms/" + keycloak.getRealm() + "/protocol/openid-connect/token")
            .build();

        this.tokenResponseClient = new RestClientTokenExchangeTokenResponseClient();
        // Own RestClient (not Boot's builder): the token endpoint needs the OAuth2 form/response converters
        this.tokenResponseClient.setRestClient(RestClient.builder()
            .requestFactory(requestFactoryBuilder.build())
            .messageConverters(converters -> {
                converters.add(0, new FormHttpMessageConverter());
                converters.add(0, new OAuth2AccessTokenResponseHttpMessageConverter());
            })
            .defaultStatusHandler(HttpStatusCode::is4xxClientError, (request, response) -> {
                throw new TokenExchangeException(
                    "Token exchange rejected by Keycloak — verify subject_token validity and client configuration: "
                        + body(response));
            })
            .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) -> {
                throw new TokenExchangeException("Keycloak server error during token exchange: " + body(response));
            })
            .build());
        // RFC 8693 audience: Spring sends grant_type, subject_token(_type) and requested_token_type itself
        this.tokenResponseClient.addParametersConverter(grantRequest -> {
            MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            if (grantRequest instanceof AudienceScopedTokenExchangeGrantRequest scoped) {
                parameters.add("audience", scoped.getAudience());
            }
            return parameters;
        });
    }

    /**
     * Exchanges a delegator token plus an actor token for a delegated, audience-scoped token.
     *
     * @param subjectToken   The delegator's access token carrying {@code may_act} (Carol)
     * @param actorToken     The actor's own access token (Dave or AI agent)
     * @param targetAudience The resource server to scope the token to (e.g. "travel-service")
     * @return TokenExchangeResponse containing the issued token (sub=delegator, act.sub=actor)
     */
    public TokenExchangeResponse exchangeDelegated(String subjectToken, String actorToken, String targetAudience) {
        if (!StringUtils.hasText(subjectToken) || !StringUtils.hasText(actorToken)) {
            throw new TokenExchangeException(
                "subject_token (delegator) and actor_token (actor) are both mandatory for delegated token exchange");
        }

        log.debug("Performing delegated token exchange: targetAudience={}", targetAudience);

        // OAuth2AccessToken (not Jwt) so Spring sends ...:token-type:access_token for both tokens
        OAuth2AccessTokenResponse tokenResponse = tokenResponseClient.getTokenResponse(
            new AudienceScopedTokenExchangeGrantRequest(registration, accessToken(subjectToken),
                accessToken(actorToken), targetAudience));

        OAuth2AccessToken issued = tokenResponse.getAccessToken();
        TokenExchangeResponse response = new TokenExchangeResponse();
        response.setAccessToken(issued.getTokenValue());
        response.setTokenType(issued.getTokenType().getValue());
        if (issued.getIssuedAt() != null && issued.getExpiresAt() != null) {
            response.setExpiresIn(Duration.between(issued.getIssuedAt(), issued.getExpiresAt()).toSeconds());
        }
        return response;
    }

    private static OAuth2AccessToken accessToken(String value) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value, null, null);
    }

    private static String body(ClientHttpResponse response) {
        try {
            return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<unreadable response body>";
        }
    }

    /** Token exchange grant request carrying the RFC 8693 {@code audience} parameter. */
    static final class AudienceScopedTokenExchangeGrantRequest extends TokenExchangeGrantRequest {

        private final String audience;

        AudienceScopedTokenExchangeGrantRequest(ClientRegistration registration, OAuth2AccessToken subjectToken,
                                                OAuth2AccessToken actorToken, String audience) {
            super(registration, subjectToken, actorToken);
            this.audience = audience;
        }

        String getAudience() {
            return audience;
        }
    }
}
