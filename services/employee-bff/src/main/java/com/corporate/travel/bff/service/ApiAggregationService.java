package com.corporate.travel.bff.service;

import com.corporate.travel.bff.client.ExpenseServiceClient;
import com.corporate.travel.bff.client.TravelServiceClient;
import com.corporate.travel.bff.model.DelegationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Aggregates data from travel-service and expense-service for the BFF dashboard endpoint.
 *
 * <p>Both service calls forward the delegation context when active, ensuring that
 * X-Delegated-Subject, X-Delegation-Id, and X-Actor-Token are threaded consistently
 * on both calls (ADR-004, ADR-018).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiAggregationService {

    private final TravelServiceClient travelServiceClient;
    private final ExpenseServiceClient expenseServiceClient;
    private final ObjectMapper objectMapper;

    /**
     * Fetches bookings and expenses and assembles them into a single dashboard response.
     *
     * @param bearerToken       The token to authenticate with downstream services
     * @param delegationContext Active delegation context, if any — forwarded to both clients
     * @return Aggregated dashboard node containing {@code bookings} and {@code expenses}
     */
    public JsonNode getDashboard(String bearerToken, Optional<DelegationContext> delegationContext) {
        log.debug("Aggregating dashboard: delegated={}", delegationContext.isPresent());

        JsonNode bookings = travelServiceClient.getBookings(bearerToken, delegationContext);
        JsonNode expenses = expenseServiceClient.getExpenses(bearerToken, delegationContext);

        ObjectNode dashboard = objectMapper.createObjectNode();
        dashboard.set("bookings", bookings);
        dashboard.set("expenses", expenses);
        return dashboard;
    }
}
