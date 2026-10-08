package com.corporate.travel.bff.client;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client for delegation-service — retrieves delegation records to resolve
 * the delegation target's user ID before performing token exchange.
 */
@Component
@Slf4j
public class DelegationServiceClient {

    private final RestClient delegationServiceRestClient;

    public DelegationServiceClient(
            @Qualifier("delegationServiceRestClient") RestClient delegationServiceRestClient) {
        this.delegationServiceRestClient = delegationServiceRestClient;
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
        return delegationServiceRestClient.get()
            .uri("/api/delegations/{id}", delegationId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .retrieve()
            .body(JsonNode.class);
    }

    /**
     * Revokes a delegation. delegation-service (via OPA) only allows the delegator to do this.
     *
     * @param delegationId UUID of the delegation
     * @param bearerToken  Delegator's Bearer token
     */
    public void revokeDelegation(String delegationId, String bearerToken) {
        log.debug("Revoking delegation: {}", delegationId);
        delegationServiceRestClient.delete()
            .uri("/api/delegations/{id}", delegationId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .retrieve()
            .toBodilessEntity();
    }
}
