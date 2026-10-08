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
 * Client for travel-service — proxies booking operations. When delegation mode is active the
 * travel-service-scoped delegation token and delegation headers are used (see
 * {@link DelegationRequestHeaders}); otherwise the caller's own token is forwarded.
 */
@Component
@Slf4j
public class TravelServiceClient {

    static final String AUDIENCE = "travel-service";

    private final RestClient travelServiceRestClient;

    public TravelServiceClient(
            @Qualifier("travelServiceRestClient") RestClient travelServiceRestClient) {
        this.travelServiceRestClient = travelServiceRestClient;
    }

    public JsonNode getBookings(String userToken, Optional<DelegationContext> delegationContext) {
        return DelegationRequestHeaders.apply(
                travelServiceRestClient.get().uri("/api/bookings"),
                AUDIENCE, userToken, delegationContext)
            .retrieve()
            .body(JsonNode.class);
    }

    public JsonNode createBooking(JsonNode requestBody, String userToken, Optional<DelegationContext> delegationContext) {
        return DelegationRequestHeaders.apply(
                travelServiceRestClient.post().uri("/api/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody),
                AUDIENCE, userToken, delegationContext)
            .retrieve()
            .body(JsonNode.class);
    }

    public JsonNode getBooking(String bookingId, String userToken, Optional<DelegationContext> delegationContext) {
        return DelegationRequestHeaders.apply(
                travelServiceRestClient.get().uri("/api/bookings/{id}", bookingId),
                AUDIENCE, userToken, delegationContext)
            .retrieve()
            .body(JsonNode.class);
    }

    public JsonNode getBookingAudit(String bookingId, String userToken, Optional<DelegationContext> delegationContext) {
        return DelegationRequestHeaders.apply(
                travelServiceRestClient.get().uri("/api/bookings/{id}/audit", bookingId),
                AUDIENCE, userToken, delegationContext)
            .retrieve()
            .body(JsonNode.class);
    }
}
