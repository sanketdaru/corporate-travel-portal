package com.corporate.travel.bff.client;

import com.corporate.travel.bff.model.DelegationContext;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Client for expense-service — proxies expense operations. When delegation mode is active the
 * expense-service-scoped delegation token and delegation headers are used (see
 * {@link DelegationRequestHeaders}); otherwise the caller's own token is forwarded.
 */
@Component
@Slf4j
public class ExpenseServiceClient {

    static final String AUDIENCE = "expense-service";

    private final RestClient expenseServiceRestClient;

    public ExpenseServiceClient(
            @Qualifier("expenseServiceRestClient") RestClient expenseServiceRestClient) {
        this.expenseServiceRestClient = expenseServiceRestClient;
    }

    public JsonNode getExpenses(String userToken, Optional<DelegationContext> delegationContext) {
        return DelegationRequestHeaders.apply(
                expenseServiceRestClient.get().uri("/api/expenses"),
                AUDIENCE, userToken, delegationContext)
            .retrieve()
            .body(JsonNode.class);
    }

    public JsonNode createExpense(JsonNode requestBody, String userToken, Optional<DelegationContext> delegationContext) {
        return DelegationRequestHeaders.apply(
                expenseServiceRestClient.post().uri("/api/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody),
                AUDIENCE, userToken, delegationContext)
            .retrieve()
            .body(JsonNode.class);
    }

    public JsonNode getExpense(String expenseId, String userToken, Optional<DelegationContext> delegationContext) {
        return DelegationRequestHeaders.apply(
                expenseServiceRestClient.get().uri("/api/expenses/{id}", expenseId),
                AUDIENCE, userToken, delegationContext)
            .retrieve()
            .body(JsonNode.class);
    }

    public JsonNode getExpenseAudit(String expenseId, String userToken, Optional<DelegationContext> delegationContext) {
        return DelegationRequestHeaders.apply(
                expenseServiceRestClient.get().uri("/api/expenses/{id}/audit", expenseId),
                AUDIENCE, userToken, delegationContext)
            .retrieve()
            .body(JsonNode.class);
    }

    public JsonNode submitExpense(String expenseId, String userToken, Optional<DelegationContext> delegationContext) {
        return DelegationRequestHeaders.apply(
                expenseServiceRestClient.post().uri("/api/expenses/{id}/submit", expenseId),
                AUDIENCE, userToken, delegationContext)
            .retrieve()
            .body(JsonNode.class);
    }
}
