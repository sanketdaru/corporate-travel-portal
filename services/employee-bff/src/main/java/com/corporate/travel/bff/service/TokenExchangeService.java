package com.corporate.travel.bff.service;

import com.corporate.travel.bff.client.ConsentServiceClient;
import com.corporate.travel.bff.client.DelegationServiceClient;
import com.corporate.travel.bff.client.KeycloakTokenExchangeClient;
import com.corporate.travel.bff.config.BffProperties;
import com.corporate.travel.bff.exception.DelegationNotFoundException;
import com.corporate.travel.bff.exception.TokenExchangeException;
import com.corporate.travel.bff.model.ConsentCheckResult;
import com.corporate.travel.bff.model.DelegationContext;
import com.corporate.travel.bff.model.TokenExchangeResponse;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates OAuth 2.0 Standard Token Exchange V2 for delegation flows.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Resolve delegation record from delegation-service → get subject's user ID</li>
 *   <li>Validate consent exists and capture the consentId (ADR-011 audit requirement)</li>
 *   <li>Call Keycloak Standard Token Exchange V2 with actor's token as subject_token (chain of trust),
 *       once per downstream audience in {@code delegation.audiences}. Each service validates
 *       {@code aud}, so each needs its own token. No requested_subject (ADR-004).</li>
 *   <li>Return a DelegationContext carrying the issued tokens, actorToken, and consentId
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
    private final BffProperties properties;

    /**
     * Performs Standard Token Exchange V2 for the given delegation, once per configured audience.
     *
     * @param delegationId   ID of the delegation record in delegation-service
     * @param actorToken     The actor's current access token (Dave's JWT) — mandatory chain of trust;
     *                       stored in context as X-Actor-Token for downstream audit (ADR-004, ADR-011)
     * @param actorId        The actor's user ID (Dave)
     * @return DelegationContext with the issued delegation tokens, actorToken, and consentId
     */
    public DelegationContext exchangeForDelegation(
            String delegationId,
            String actorToken,
            String actorId) {

        // Step 1: Resolve delegation → get subject's user ID
        JsonNode delegation = delegationServiceClient.getDelegation(delegationId, actorToken);
        if (delegation == null) {
            throw new DelegationNotFoundException(delegationId);
        }

        String subjectId = delegation.path("delegatorId").asString();
        if (subjectId.isBlank()) {
            throw new TokenExchangeException("Delegation record is missing delegatorId: " + delegationId);
        }

        List<String> audiences = properties.getDelegation().getAudiences();
        log.debug("Token exchange: actor={}, subject={}, audiences={}", actorId, subjectId, audiences);

        // Step 2: Validate consent and capture consentId for downstream audit records (ADR-011)
        String purpose = delegation.path("purpose").asString("book_travel");
        List<String> scopes = new ArrayList<>();
        delegation.path("scopes").forEach(s -> scopes.add(s.asString()));
        if (scopes.isEmpty()) { scopes.add("view_bookings"); }

        // Step 2 throws TokenExchangeException directly on any failure (HTTP error, unreachable,
        // or valid=false with a reason). No silent swallowing here.
        ConsentCheckResult consentResult = consentServiceClient.hasConsentForScopes(
            subjectId, actorId, purpose, scopes, actorToken);

        // Step 3: Perform Standard Token Exchange V2 per audience — actorToken is the mandatory
        // subject_token. Each token is scoped to one service. No requested_subject (ADR-004).
        Map<String, String> tokens = new LinkedHashMap<>();
        Instant expiresAt = null;
        for (String audience : audiences) {
            TokenExchangeResponse exchangeResponse = keycloakTokenExchangeClient.exchangeToken(actorToken, audience);
            tokens.put(audience, exchangeResponse.getAccessToken());
            Instant tokenExpiry = Instant.now().plusSeconds(
                exchangeResponse.getExpiresIn() != null ? exchangeResponse.getExpiresIn() : 300);
            expiresAt = expiresAt == null || tokenExpiry.isBefore(expiresAt) ? tokenExpiry : expiresAt;
        }

        return DelegationContext.builder()
            .delegationId(delegationId)
            .actorId(actorId)
            .subjectId(subjectId)
            .audiences(List.copyOf(audiences))
            .purpose(purpose)
            .delegationTokens(Map.copyOf(tokens))
            .actorToken(actorToken)
            .consentId(consentResult.getConsentId())
            .expiresAt(expiresAt)
            .build();
    }
}
