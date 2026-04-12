package com.corporate.travel.bff.client;

import com.corporate.travel.bff.model.DelegationContext;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

/**
 * Client for expense-service — proxies expense operations, threading the full delegation
 * identity context as headers when delegation mode is active (ADR-004, ADR-018, ADR-011).
 *
 * <p>When {@code delegationContext} is present, every request carries:</p>
 * <ul>
 *   <li>{@code Authorization} — audience-scoped delegation token (sub=actor)</li>
 *   <li>{@code X-Delegated-Subject} — human principal being acted for</li>
 *   <li>{@code X-Delegation-Id} — validated delegation record UUID</li>
 *   <li>{@code X-Actor-Token} — original actor JWT for audit chain reconstruction</li>
 * </ul>
 */
@Component
@Slf4j
public class ExpenseServiceClient {

    private final WebClient expenseServiceWebClient;

    public ExpenseServiceClient(
            @Qualifier("expenseServiceWebClient") WebClient expenseServiceWebClient) {
        this.expenseServiceWebClient = expenseServiceWebClient;
    }

    public JsonNode getExpenses(String bearerToken, Optional<DelegationContext> delegationContext) {
        return applyDelegationHeaders(
                expenseServiceWebClient.get().uri("/api/expenses"),
                bearerToken, delegationContext)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }

    public JsonNode createExpense(JsonNode requestBody, String bearerToken, Optional<DelegationContext> delegationContext) {
        return applyDelegationHeaders(
                expenseServiceWebClient.post().uri("/api/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody),
                bearerToken, delegationContext)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }

    public JsonNode getExpense(String expenseId, String bearerToken, Optional<DelegationContext> delegationContext) {
        return applyDelegationHeaders(
                expenseServiceWebClient.get().uri("/api/expenses/{id}", expenseId),
                bearerToken, delegationContext)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }

    public JsonNode getExpenseAudit(String expenseId, String bearerToken, Optional<DelegationContext> delegationContext) {
        return applyDelegationHeaders(
                expenseServiceWebClient.get().uri("/api/expenses/{id}/audit", expenseId),
                bearerToken, delegationContext)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }

    public JsonNode submitExpense(String expenseId, String bearerToken, Optional<DelegationContext> delegationContext) {
        return applyDelegationHeaders(
                expenseServiceWebClient.post().uri("/api/expenses/{id}/submit", expenseId),
                bearerToken, delegationContext)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }

    /**
     * Applies Authorization plus delegation headers when a delegation context is active.
     * Headers are only added when delegation is present — they must never be sent as blank
     * values, since downstream services use header presence to detect delegation mode.
     */
    private WebClient.RequestHeadersSpec<?> applyDelegationHeaders(
            WebClient.RequestHeadersSpec<?> spec,
            String bearerToken,
            Optional<DelegationContext> delegationContext) {

        spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        if (delegationContext.isPresent()) {
            DelegationContext ctx = delegationContext.get();
            spec = spec
                .header("X-Delegated-Subject", ctx.getSubjectId())
                .header("X-Delegation-Id", ctx.getDelegationId())
                .header("X-Actor-Token", ctx.getActorToken());
            if (ctx.getConsentId() != null) {
                spec = spec.header("X-Consent-Id", ctx.getConsentId());
            }
            if (ctx.getPurpose() != null) {
                spec = spec.header("X-Delegation-Purpose", ctx.getPurpose());
            }
        }
        return spec;
    }
}
