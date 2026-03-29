package com.corporate.travel.bff.client;

import com.corporate.travel.bff.model.ConsentCheckResult;
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
     * Checks whether active consent exists covering the requested scopes and returns the consent ID.
     *
     * <p>The consentId is required by ADR-011 so that downstream services can record it in their
     * audit tables. Returning a boolean alone would permanently lose this reference.</p>
     *
     * @param grantorId   Subject's user ID (e.g. Carol)
     * @param granteeId   Actor's user ID (e.g. Dave)
     * @param scopes      Scopes required for this delegation
     * @param bearerToken Caller's Bearer token for authentication
     * @return ConsentCheckResult with valid=true and the consentId if found; valid=false otherwise
     */
    public ConsentCheckResult hasConsentForScopes(String grantorId, String granteeId, List<String> scopes, String bearerToken) {
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

            if (response == null || !response.isArray() || response.size() == 0) {
                return new ConsentCheckResult(false, null);
            }

            String consentId = response.get(0).path("id").asText(null);
            return new ConsentCheckResult(true, consentId);
        } catch (Exception e) {
            log.warn("Consent check failed for grantor={} grantee={}: {}", grantorId, granteeId, e.getMessage());
            return new ConsentCheckResult(false, null);
        }
    }
}
