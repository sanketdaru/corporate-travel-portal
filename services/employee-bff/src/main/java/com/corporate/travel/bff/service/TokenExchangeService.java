package com.corporate.travel.bff.service;

import com.corporate.travel.bff.client.ConsentServiceClient;
import com.corporate.travel.bff.client.DelegationServiceClient;
import com.corporate.travel.bff.client.KeycloakTokenExchangeClient;
import com.corporate.travel.bff.exception.DelegationNotFoundException;
import com.corporate.travel.bff.exception.TokenExchangeException;
import com.corporate.travel.bff.model.DelegationContext;
import com.corporate.travel.bff.model.TokenExchangeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates OAuth 2.0 Standard Token Exchange V2 for delegation flows.
 *
 * Flow:
 *   1. Resolve delegation record from delegation-service → get subject's user ID
 *   2. Validate consent exists between actor and subject
 *   3. Call Keycloak token exchange with actor's token as subject_token (chain of trust)
 *   4. Return a DelegationContext containing the issued delegation token
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenExchangeService {

    private final DelegationServiceClient delegationServiceClient;
    private final ConsentServiceClient consentServiceClient;
    private final KeycloakTokenExchangeClient keycloakTokenExchangeClient;

    /**
     * Performs token exchange for the given delegation and target audience.
     *
     * @param delegationId   ID of the delegation record in delegation-service
     * @param actorToken     The actor's current access token (Dave's JWT) — mandatory chain of trust
     * @param actorId        The actor's user ID (Dave)
     * @param targetAudience The resource server audience (e.g. "travel-service")
     * @return DelegationContext with the issued delegation token
     */
    public DelegationContext exchangeForDelegation(
            String delegationId,
            String actorToken,
            String actorId,
            String targetAudience) {

        // Step 1: Resolve delegation → get subject's user ID
        JsonNode delegation = delegationServiceClient.getDelegation(delegationId, actorToken);
        if (delegation == null) {
            throw new DelegationNotFoundException(delegationId);
        }

        String subjectId = delegation.path("delegatorId").asText();
        if (subjectId.isBlank()) {
            throw new TokenExchangeException("Delegation record is missing delegatorId: " + delegationId);
        }

        log.debug("Token exchange: actor={}, subject={}, audience={}", actorId, subjectId, targetAudience);

        // Step 2: Validate consent
        boolean hasConsent = consentServiceClient.hasConsentForScopes(
            subjectId, actorId, List.of("book_travel"), actorToken);
        if (!hasConsent) {
            throw new TokenExchangeException(
                "No active consent found for actor=" + actorId + " acting on behalf of subject=" + subjectId);
        }

        // Step 3: Perform Standard Token Exchange V2 — actorToken is the mandatory subject_token
        TokenExchangeResponse exchangeResponse = keycloakTokenExchangeClient.exchangeToken(
            actorToken, subjectId, targetAudience);

        return DelegationContext.builder()
            .delegationId(delegationId)
            .actorId(actorId)
            .subjectId(subjectId)
            .audience(targetAudience)
            .delegationToken(exchangeResponse.getAccessToken())
            .expiresAt(Instant.now().plusSeconds(
                exchangeResponse.getExpiresIn() != null ? exchangeResponse.getExpiresIn() : 300))
            .build();
    }
}
