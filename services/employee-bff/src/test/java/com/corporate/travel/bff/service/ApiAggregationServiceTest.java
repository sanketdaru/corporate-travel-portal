package com.corporate.travel.bff.service;

import com.corporate.travel.bff.client.ExpenseServiceClient;
import com.corporate.travel.bff.client.TravelServiceClient;
import com.corporate.travel.bff.model.DelegationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiAggregationServiceTest {

    @Mock
    private TravelServiceClient travelServiceClient;
    @Mock
    private ExpenseServiceClient expenseServiceClient;

    private ApiAggregationService apiAggregationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        apiAggregationService = new ApiAggregationService(
            travelServiceClient, expenseServiceClient, objectMapper);
    }

    @Test
    void getDashboard_withoutDelegation_callsBothServicesAndAssemblesResponse() {
        ArrayNode bookings = objectMapper.createArrayNode();
        bookings.addObject().put("id", "booking-1");

        ArrayNode expenses = objectMapper.createArrayNode();
        expenses.addObject().put("id", "expense-1");

        when(travelServiceClient.getBookings("user-token", Optional.empty())).thenReturn(bookings);
        when(expenseServiceClient.getExpenses("user-token", Optional.empty())).thenReturn(expenses);

        JsonNode result = apiAggregationService.getDashboard("user-token", Optional.empty());

        assertThat(result.get("bookings")).isEqualTo(bookings);
        assertThat(result.get("expenses")).isEqualTo(expenses);
    }

    @Test
    void getDashboard_withDelegation_forwardsDelegationContextToBothClients() {
        DelegationContext ctx = DelegationContext.builder()
            .delegationId("delegation-uuid")
            .actorId("dave-id")
            .subjectId("carol-id")
            .audience("travel-service")
            .delegationToken("delegation-token")
            .actorToken("dave-original-token")
            .consentId("consent-uuid-abc")
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

        ObjectNode bookings = objectMapper.createObjectNode();
        ObjectNode expenses = objectMapper.createObjectNode();

        when(travelServiceClient.getBookings(eq("delegation-token"), eq(Optional.of(ctx))))
            .thenReturn(bookings);
        when(expenseServiceClient.getExpenses(eq("delegation-token"), eq(Optional.of(ctx))))
            .thenReturn(expenses);

        apiAggregationService.getDashboard("delegation-token", Optional.of(ctx));

        // Verify delegation context was forwarded to both downstream clients (ADR-004, ADR-018)
        verify(travelServiceClient).getBookings("delegation-token", Optional.of(ctx));
        verify(expenseServiceClient).getExpenses("delegation-token", Optional.of(ctx));
    }
}
