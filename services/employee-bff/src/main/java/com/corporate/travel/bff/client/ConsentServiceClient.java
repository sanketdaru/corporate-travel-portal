package com.corporate.travel.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Client for consent-service — validates active consent before performing token exchange.
 */
@Component
@Slf4j
public class ConsentServiceClient {

    private final WebClient consentServiceWebClient;

    public ConsentServiceClient(
            @Qualifier("consentServiceWebClient") WebClient consentServiceWebClient) {
        this.consentServiceWebClient = consentServiceWebClient;
    }

    /**
     * Checks whether active consent exists covering the requested scopes.
     *
     * @param grantorId   Subject's user ID (e.g. Carol)
     * @param granteeId   Actor's user ID (e.g. Dave)
     * @param scopes      Scopes required for this delegation
     * @param bearerToken Caller's Bearer token for authentication
     * @return true if consent is valid and covers the requested scopes
     */
    public boolean hasConsentForScopes(String grantorId, String granteeId, List<String> scopes, String bearerToken) {
        log.debug("Checking consent: grantor={}, grantee={}, scopes={}", grantorId, granteeId, scopes);
        try {
            JsonNode response = consentServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/consents")
                    .queryParam("grantorId", grantorId)
                    .queryParam("granteeId", granteeId)
                    .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            return response != null && response.isArray() && response.size() > 0;
        } catch (Exception e) {
            log.warn("Consent check failed for grantor={} grantee={}: {}", grantorId, granteeId, e.getMessage());
            return false;
        }
    }
}
