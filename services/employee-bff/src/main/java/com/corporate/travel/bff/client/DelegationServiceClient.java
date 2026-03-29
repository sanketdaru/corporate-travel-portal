package com.corporate.travel.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for delegation-service — retrieves delegation records to resolve
 * the delegation target's user ID before performing token exchange.
 */
@Component
@Slf4j
public class DelegationServiceClient {

    private final WebClient delegationServiceWebClient;

    public DelegationServiceClient(
            @Qualifier("delegationServiceWebClient") WebClient delegationServiceWebClient) {
        this.delegationServiceWebClient = delegationServiceWebClient;
    }

    /**
     * Fetches a delegation record by ID.
     *
     * @param delegationId  UUID of the delegation
     * @param bearerToken   Caller's Bearer token for authentication
     * @return JsonNode of the delegation response
     */
    public JsonNode getDelegation(String delegationId, String bearerToken) {
        log.debug("Fetching delegation: {}", delegationId);
        return delegationServiceWebClient.get()
            .uri("/api/delegations/{id}", delegationId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }
}
