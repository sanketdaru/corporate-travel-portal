package com.corporate.travel.expense.client;

import com.corporate.travel.expense.exception.LinkedBookingNotFoundException;
import com.corporate.travel.expense.exception.TravelServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Internal service-to-service client for the Travel Service.
 *
 * Used exclusively by the expense service to fetch booking budget information
 * before allowing expense submission. Calls are made over the internal container
 * network (travel-service:8081) without going through the API gateway.
 *
 * Calls carry the expense-service service-account token (client credentials, see
 * TravelServiceClientConfig and ADR-009), not the user's token.
 */
@Component
@Slf4j
public class TravelServiceClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public TravelServiceClient(@Qualifier("travelServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Booking projection — only the fields needed for budget validation.
     */
    public record BookingBudget(UUID id, BigDecimal budget, String budgetCurrency) {}

    /**
     * Fetch budget details for a booking in the given tenant.
     *
     * The budget check fails closed: a booking that does not exist in that tenant throws
     * {@link LinkedBookingNotFoundException}; any other failure (travel service down, token
     * rejected, malformed response) throws {@link TravelServiceUnavailableException}.
     */
    public BookingBudget getBookingBudget(UUID bookingId, String tenantId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uri -> uri.path("/api/bookings/{id}/budget")
                            .queryParam("tenantId", tenantId)
                            .build(bookingId))
                    .retrieve()
                    .body(JSON_OBJECT);
            if (body == null || body.get("budget") == null) {
                throw new IllegalStateException("response has no budget");
            }

            BigDecimal budget = new BigDecimal(body.get("budget").toString());
            String currency   = body.getOrDefault("budgetCurrency", "INR").toString();
            return new BookingBudget(bookingId, budget, currency);

        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Booking {} not found in tenant {} — refusing submission", bookingId, tenantId);
            throw new LinkedBookingNotFoundException(bookingId);
        } catch (Exception ex) {
            log.error("Failed to fetch budget for booking {} from travel service: {}", bookingId, ex.getMessage());
            throw new TravelServiceUnavailableException(bookingId, ex);
        }
    }
}
