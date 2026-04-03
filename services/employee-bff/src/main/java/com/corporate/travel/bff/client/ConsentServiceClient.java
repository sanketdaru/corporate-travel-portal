package com.corporate.travel.bff.client;

import com.corporate.travel.bff.model.ConsentCheckResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final ObjectMapper objectMapper;

    public ConsentServiceClient(
            @Qualifier("consentServiceWebClient") WebClient consentServiceWebClient,
            ObjectMapper objectMapper) {
        this.consentServiceWebClient = consentServiceWebClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks whether active consent exists covering the requested scopes and returns the consent ID.
     *
     * <p>Uses the {@code POST /api/consents/validate} endpoint. The consentId is required by
     * ADR-011 so that downstream services can record it in their audit tables.</p>
     *
     * @param grantorId   Subject's user ID (e.g. Carol)
     * @param granteeId   Actor's user ID (e.g. Dave)
     * @param purpose     Consent purpose matching the delegation record (e.g. "book_travel")
     * @param scopes      Scopes required for this delegation
     * @param bearerToken Caller's Bearer token for authentication
     * @return ConsentCheckResult with valid=true and the consentId if found; valid=false otherwise
     */
    public ConsentCheckResult hasConsentForScopes(String grantorId, String granteeId, String purpose, List<String> scopes, String bearerToken) {
        log.debug("Checking consent: grantor={}, grantee={}, purpose={}, scopes={}", grantorId, granteeId, purpose, scopes);
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("grantorId", grantorId);
            body.put("granteeId", granteeId);
            body.put("purpose", purpose);
            ArrayNode scopesNode = body.putArray("scopes");
            scopes.forEach(scopesNode::add);

            JsonNode response = consentServiceWebClient.post()
                .uri("/api/consents/validate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            if (response == null || !response.path("valid").asBoolean(false)) {
                return new ConsentCheckResult(false, null);
            }

            String consentId = response.path("consentId").asText(null);
            return new ConsentCheckResult(true, consentId);
        } catch (Exception e) {
            log.warn("Consent check failed for grantor={} grantee={}: {}", grantorId, granteeId, e.getMessage());
            return new ConsentCheckResult(false, null);
        }
    }
}
