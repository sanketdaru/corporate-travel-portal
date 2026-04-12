package com.corporate.travel.bff.service;

import com.corporate.travel.bff.client.ConsentServiceClient;
import com.corporate.travel.bff.client.DelegationServiceClient;
import com.corporate.travel.bff.client.KeycloakTokenExchangeClient;
import com.corporate.travel.bff.exception.DelegationNotFoundException;
import com.corporate.travel.bff.exception.TokenExchangeException;
import com.corporate.travel.bff.model.ConsentCheckResult;
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
 * <p>Flow:</p>
 * <ol>
 *   <li>Resolve delegation record from delegation-service → get subject's user ID</li>
 *   <li>Validate consent exists and capture the consentId (ADR-011 audit requirement)</li>
 *   <li>Call Keycloak Standard Token Exchange V2 with actor's token as subject_token (chain of trust).
 *       Audience-scoped only — no requested_subject (ADR-004).</li>
 *   <li>Return a DelegationContext carrying the issued token, actorToken, and consentId
 *       so the BFF can thread all delegation headers on downstream calls.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenExchangeService {

    private final DelegationServiceClient delegationServiceClient;
    private final ConsentServiceClient consentServiceClient;
    private final KeycloakTokenExchangeClient keycloakTokenExchangeClient;

    /**
     * Performs Standard Token Exchange V2 for the given delegation and target audience.
     *
     * @param delegationId   ID of the delegation record in delegation-service
     * @param actorToken     The actor's current access token (Dave's JWT) — mandatory chain of trust;
     *                       stored in context as X-Actor-Token for downstream audit (ADR-004, ADR-011)
     * @param actorId        The actor's user ID (Dave)
     * @param targetAudience The resource server audience (e.g. "travel-service")
     * @return DelegationContext with the issued delegation token, actorToken, and consentId
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

        // Step 2: Validate consent and capture consentId for downstream audit records (ADR-011)
        String purpose = delegation.path("purpose").asText("book_travel");
        List<String> scopes = new java.util.ArrayList<>();
        delegation.path("scopes").forEach(s -> scopes.add(s.asText()));
        if (scopes.isEmpty()) { scopes.add("view_bookings"); }

        // Step 2 throws TokenExchangeException directly on any failure (HTTP error, unreachable,
        // or valid=false with a reason). No silent swallowing here.
        ConsentCheckResult consentResult = consentServiceClient.hasConsentForScopes(
            subjectId, actorId, purpose, scopes, actorToken);

        // Step 3: Perform Standard Token Exchange V2 — actorToken is the mandatory subject_token.
        // audience scopes the token to the target service. No requested_subject (ADR-004).
        TokenExchangeResponse exchangeResponse = keycloakTokenExchangeClient.exchangeToken(
            actorToken, targetAudience);

        return DelegationContext.builder()
            .delegationId(delegationId)
            .actorId(actorId)
            .subjectId(subjectId)
            .audience(targetAudience)
            .purpose(purpose)
            .delegationToken(exchangeResponse.getAccessToken())
            .actorToken(actorToken)
            .consentId(consentResult.getConsentId())
            .expiresAt(Instant.now().plusSeconds(
                exchangeResponse.getExpiresIn() != null ? exchangeResponse.getExpiresIn() : 300))
            .build();
    }
}
