package com.corporate.travel.bff.client;

import com.corporate.travel.bff.model.DelegationContext;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Verifies that TravelServiceClient threads the correct delegation headers on downstream
 * calls when a delegation context is active, and omits them otherwise (ADR-004, ADR-018, ADR-011).
 */
class TravelServiceClientTest {

    private WireMockServer wireMockServer;
    private TravelServiceClient client;

    private static final String BEARER_TOKEN = "test-bearer-token";
    private static final String DELEGATION_TOKEN = "delegation-token";
    private static final String ACTOR_TOKEN = "dave-original-token";
    private static final String SUBJECT_ID = "carol-user-id";
    private static final String DELEGATION_ID = "delegation-uuid-123";

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:" + wireMockServer.port())
            .build();

        client = new TravelServiceClient(webClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ── getBookings ────────────────────────────────────────────────────────────

    @Test
    void getBookings_withoutDelegation_sendsOnlyAuthorizationHeader() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/bookings"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));

        client.getBookings(BEARER_TOKEN, Optional.empty());

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/bookings"))
            .withHeader("Authorization", equalTo("Bearer " + BEARER_TOKEN))
            .withoutHeader("X-Delegated-Subject")
            .withoutHeader("X-Delegation-Id")
            .withoutHeader("X-Actor-Token"));
    }

    @Test
    void getBookings_withDelegation_sendsAllDelegationHeaders() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/bookings"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));

        client.getBookings(DELEGATION_TOKEN, Optional.of(buildDelegationContext()));

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/bookings"))
            .withHeader("Authorization", equalTo("Bearer " + DELEGATION_TOKEN))
            .withHeader("X-Delegated-Subject", equalTo(SUBJECT_ID))
            .withHeader("X-Delegation-Id", equalTo(DELEGATION_ID))
            .withHeader("X-Actor-Token", equalTo(ACTOR_TOKEN)));
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBooking_withoutDelegation_sendsOnlyAuthorizationHeader() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/bookings"))
            .willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("destination", "London");
        client.createBooking(body, BEARER_TOKEN, Optional.empty());

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/bookings"))
            .withHeader("Authorization", equalTo("Bearer " + BEARER_TOKEN))
            .withoutHeader("X-Delegated-Subject")
            .withoutHeader("X-Delegation-Id")
            .withoutHeader("X-Actor-Token"));
    }

    @Test
    void createBooking_withDelegation_sendsAllDelegationHeaders() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/bookings"))
            .willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("destination", "London");
        client.createBooking(body, DELEGATION_TOKEN, Optional.of(buildDelegationContext()));

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/bookings"))
            .withHeader("Authorization", equalTo("Bearer " + DELEGATION_TOKEN))
            .withHeader("X-Delegated-Subject", equalTo(SUBJECT_ID))
            .withHeader("X-Delegation-Id", equalTo(DELEGATION_ID))
            .withHeader("X-Actor-Token", equalTo(ACTOR_TOKEN)));
    }

    // ── getBooking ────────────────────────────────────────────────────────────

    @Test
    void getBooking_withoutDelegation_sendsOnlyAuthorizationHeader() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/bookings/booking-1"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        client.getBooking("booking-1", BEARER_TOKEN, Optional.empty());

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/bookings/booking-1"))
            .withHeader("Authorization", equalTo("Bearer " + BEARER_TOKEN))
            .withoutHeader("X-Delegated-Subject")
            .withoutHeader("X-Delegation-Id")
            .withoutHeader("X-Actor-Token"));
    }

    @Test
    void getBooking_withDelegation_sendsAllDelegationHeaders() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/bookings/booking-1"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        client.getBooking("booking-1", DELEGATION_TOKEN, Optional.of(buildDelegationContext()));

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/bookings/booking-1"))
            .withHeader("Authorization", equalTo("Bearer " + DELEGATION_TOKEN))
            .withHeader("X-Delegated-Subject", equalTo(SUBJECT_ID))
            .withHeader("X-Delegation-Id", equalTo(DELEGATION_ID))
            .withHeader("X-Actor-Token", equalTo(ACTOR_TOKEN)));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private DelegationContext buildDelegationContext() {
        return DelegationContext.builder()
            .delegationId(DELEGATION_ID)
            .actorId("dave-user-id")
            .subjectId(SUBJECT_ID)
            .audience("travel-service")
            .delegationToken(DELEGATION_TOKEN)
            .actorToken(ACTOR_TOKEN)
            .consentId("consent-uuid-abc")
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    }
}
