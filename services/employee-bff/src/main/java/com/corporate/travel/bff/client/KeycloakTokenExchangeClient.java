package com.corporate.travel.bff.client;

import com.corporate.travel.bff.config.BffProperties;
import com.corporate.travel.bff.exception.TokenExchangeException;
import com.corporate.travel.bff.model.TokenExchangeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Performs OAuth 2.0 Standard Token Exchange V2 (RFC 8693) against Keycloak.
 *
 * Security contract:
 *   - actorToken (subject_token) is MANDATORY. It proves the actor's identity and
 *     establishes the chain of trust. Keycloak rejects requests without it. This is
 *     what distinguishes Standard Token Exchange V2 from the deprecated naked exchange.
 *   - audience scopes the resulting token to a specific resource server.
 *   - requestedSubject identifies the delegation target (e.g. Carol's user ID) and
 *     requires employee-bff to hold the token-exchanger role via Client Policy.
 */
@Component
@Slf4j
public class KeycloakTokenExchangeClient {

    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String TOKEN_TYPE_ACCESS = "urn:ietf:params:oauth:token-type:access_token";

    private final WebClient keycloakWebClient;
    private final BffProperties properties;

    public KeycloakTokenExchangeClient(
            @Qualifier("keycloakWebClient") WebClient keycloakWebClient,
            BffProperties properties) {
        this.keycloakWebClient = keycloakWebClient;
        this.properties = properties;
    }

    /**
     * Exchanges the actor's token for an audience-scoped token via Standard Token Exchange V2 (RFC 8693).
     *
     * <p>Security contract:</p>
     * <ul>
     *   <li>actorToken (subject_token) is MANDATORY — proves actor identity and establishes the chain
     *       of trust. Keycloak rejects requests without it.</li>
     *   <li>audience scopes the resulting token to a single resource server, preventing replay.</li>
     *   <li>NO requested_subject — Standard V2 does not support impersonation. The delegation target
     *       (e.g. Carol) is carried as the X-Delegated-Subject application header, validated against
     *       the delegation-service before this exchange is invoked (ADR-004).</li>
     * </ul>
     *
     * @param actorToken     The actor's current access token (Dave or AI agent) — chain of trust, mandatory
     * @param targetAudience The resource server to scope the token to (e.g. "travel-service")
     * @return TokenExchangeResponse containing the issued audience-scoped token
     */
    public TokenExchangeResponse exchangeToken(
            String actorToken,
            String targetAudience) {

        if (!StringUtils.hasText(actorToken)) {
            throw new TokenExchangeException(
                "subject_token (actorToken) is mandatory for Standard Token Exchange V2 — chain of trust cannot be established without it");
        }

        log.debug("Performing token exchange: targetAudience={}", targetAudience);

        String tokenUrl = String.format("/realms/%s/protocol/openid-connect/token",
            properties.getKeycloak().getRealm());

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", GRANT_TYPE);
        formData.add("client_id", properties.getKeycloak().getClientId());
        formData.add("client_secret", properties.getKeycloak().getClientSecret());
        // subject_token: the actor's existing token — mandatory chain of trust for Standard V2
        formData.add("subject_token", actorToken);
        formData.add("subject_token_type", TOKEN_TYPE_ACCESS);
        formData.add("requested_token_type", TOKEN_TYPE_ACCESS);
        // audience: scopes the issued token to the specific downstream resource server
        formData.add("audience", targetAudience);
        // Note: NO requested_subject — Standard V2 does not support impersonation.
        // The delegation target is carried as X-Delegated-Subject header (ADR-004 Layer 2).

        return keycloakWebClient.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(formData)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new TokenExchangeException(
                        "Token exchange rejected by Keycloak — verify subject_token validity and Client Policy configuration: " + body))))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new TokenExchangeException(
                        "Keycloak server error during token exchange: " + body))))
            .bodyToMono(TokenExchangeResponse.class)
            .block();
    }
}
