package com.corporate.travel.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for travel-service — proxies booking operations using the caller's token
 * or a delegation token when delegation mode is active.
 */
@Component
@Slf4j
public class TravelServiceClient {

    private final WebClient travelServiceWebClient;

    public TravelServiceClient(
            @Qualifier("travelServiceWebClient") WebClient travelServiceWebClient) {
        this.travelServiceWebClient = travelServiceWebClient;
    }

    public JsonNode getBookings(String bearerToken) {
        return travelServiceWebClient.get()
            .uri("/api/bookings")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }

    public JsonNode createBooking(JsonNode requestBody, String bearerToken) {
        return travelServiceWebClient.post()
            .uri("/api/bookings")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }

    public JsonNode getBooking(String bookingId, String bearerToken) {
        return travelServiceWebClient.get()
            .uri("/api/bookings/{id}", bookingId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }
}
