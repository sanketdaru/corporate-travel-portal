package com.corporate.travel.expense.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal service-to-service client for the Travel Service.
 *
 * Used exclusively by the expense service to fetch booking budget information
 * before allowing expense submission. Calls are made over the internal container
 * network (travel-service:8081) without going through the API gateway.
 *
 * No JWT is forwarded — this is a trusted internal call between microservices
 * on the same Docker/Podman network.
 */
@Component
@Slf4j
public class TravelServiceClient {

    private final RestTemplate restTemplate;
    private final String travelServiceUrl;

    public TravelServiceClient(
            RestTemplate restTemplate,
            @Value("${travel-service.url:http://travel-service:8081}") String travelServiceUrl) {
        this.restTemplate = restTemplate;
        this.travelServiceUrl = travelServiceUrl;
    }

    /**
     * Booking projection — only the fields needed for budget validation.
     */
    public record BookingBudget(UUID id, BigDecimal budget, String budgetCurrency) {}

    /**
     * Fetch budget details for a booking.
     *
     * Returns empty if the booking cannot be found or the travel service is
     * unavailable. The caller decides whether a missing budget means allow or deny.
     */
    @SuppressWarnings("unchecked")
    public Optional<BookingBudget> getBookingBudget(UUID bookingId) {
        String url = travelServiceUrl + "/api/bookings/" + bookingId + "/budget";
        try {
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            if (body == null) return Optional.empty();

            BigDecimal budget = new BigDecimal(body.get("budget").toString());
            String currency   = body.getOrDefault("budgetCurrency", "INR").toString();
            return Optional.of(new BookingBudget(bookingId, budget, currency));

        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Booking {} not found in travel service — skipping budget check", bookingId);
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Failed to fetch budget for booking {} from travel service: {}", bookingId, ex.getMessage());
            return Optional.empty();
        }
    }
}
