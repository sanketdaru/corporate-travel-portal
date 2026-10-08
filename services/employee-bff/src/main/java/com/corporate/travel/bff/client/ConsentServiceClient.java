package com.corporate.travel.bff.client;

import com.corporate.travel.bff.exception.TokenExchangeException;
import com.corporate.travel.bff.model.ConsentCheckResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Client for consent-service — validates active consent before performing token exchange.
 */
@Component
@Slf4j
public class ConsentServiceClient {

    private final RestClient consentServiceRestClient;
    private final ObjectMapper objectMapper;

    public ConsentServiceClient(
            @Qualifier("consentServiceRestClient") RestClient consentServiceRestClient,
            ObjectMapper objectMapper) {
        this.consentServiceRestClient = consentServiceRestClient;
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

            JsonNode response = consentServiceRestClient.post()
                .uri("/api/consents/validate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

            if (response == null || !response.path("valid").asBoolean(false)) {
                // Consent-service returned HTTP 200 but valid=false — surface the reason so the
                // caller can show a meaningful error instead of a generic "no consent" message.
                String reason = response != null ? response.path("reason").asString("No active consent found") : "Empty response from consent service";
                throw new TokenExchangeException("Consent validation failed: " + reason);
            }

            String consentId = response.path("consentId").asString(null);
            return new ConsentCheckResult(true, consentId);
        } catch (TokenExchangeException e) {
            throw e; // already formatted — let it propagate
        } catch (RestClientResponseException e) {
            // HTTP error from consent-service (4xx/5xx). Extract the response body so the real
            // cause (e.g. OPA 403, NPE 500) is visible in the frontend error message.
            String body = e.getResponseBodyAsString();
            log.error("Consent check failed for grantor={} grantee={}: HTTP {} - {}",
                grantorId, granteeId, e.getStatusCode(), body);
            throw new TokenExchangeException(
                "Consent service returned HTTP " + e.getStatusCode().value() + ": " + body);
        } catch (Exception e) {
            log.error("Consent check failed for grantor={} grantee={}: {}", grantorId, granteeId, e.getMessage(), e);
            throw new TokenExchangeException("Consent service unreachable: " + e.getMessage());
        }
    }
}
