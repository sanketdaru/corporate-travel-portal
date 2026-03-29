package com.corporate.travel.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for expense-service — proxies expense operations using the caller's token
 * or a delegation token when delegation mode is active.
 */
@Component
@Slf4j
public class ExpenseServiceClient {

    private final WebClient expenseServiceWebClient;

    public ExpenseServiceClient(
            @Qualifier("expenseServiceWebClient") WebClient expenseServiceWebClient) {
        this.expenseServiceWebClient = expenseServiceWebClient;
    }

    public JsonNode getExpenses(String bearerToken) {
        return expenseServiceWebClient.get()
            .uri("/api/expenses")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }

    public JsonNode createExpense(JsonNode requestBody, String bearerToken) {
        return expenseServiceWebClient.post()
            .uri("/api/expenses")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }

    public JsonNode getExpense(String expenseId, String bearerToken) {
        return expenseServiceWebClient.get()
            .uri("/api/expenses/{id}", expenseId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }
}
