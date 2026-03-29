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
 * Verifies that ExpenseServiceClient threads the correct delegation headers on downstream
 * calls when a delegation context is active, and omits them otherwise (ADR-004, ADR-018, ADR-011).
 */
class ExpenseServiceClientTest {

    private WireMockServer wireMockServer;
    private ExpenseServiceClient client;

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

        client = new ExpenseServiceClient(webClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ── getExpenses ───────────────────────────────────────────────────────────

    @Test
    void getExpenses_withoutDelegation_sendsOnlyAuthorizationHeader() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/expenses"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));

        client.getExpenses(BEARER_TOKEN, Optional.empty());

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/expenses"))
            .withHeader("Authorization", equalTo("Bearer " + BEARER_TOKEN))
            .withoutHeader("X-Delegated-Subject")
            .withoutHeader("X-Delegation-Id")
            .withoutHeader("X-Actor-Token"));
    }

    @Test
    void getExpenses_withDelegation_sendsAllDelegationHeaders() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/expenses"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));

        client.getExpenses(DELEGATION_TOKEN, Optional.of(buildDelegationContext()));

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/expenses"))
            .withHeader("Authorization", equalTo("Bearer " + DELEGATION_TOKEN))
            .withHeader("X-Delegated-Subject", equalTo(SUBJECT_ID))
            .withHeader("X-Delegation-Id", equalTo(DELEGATION_ID))
            .withHeader("X-Actor-Token", equalTo(ACTOR_TOKEN)));
    }

    // ── createExpense ─────────────────────────────────────────────────────────

    @Test
    void createExpense_withoutDelegation_sendsOnlyAuthorizationHeader() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/expenses"))
            .willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("amount", 150);
        client.createExpense(body, BEARER_TOKEN, Optional.empty());

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/expenses"))
            .withHeader("Authorization", equalTo("Bearer " + BEARER_TOKEN))
            .withoutHeader("X-Delegated-Subject")
            .withoutHeader("X-Delegation-Id")
            .withoutHeader("X-Actor-Token"));
    }

    @Test
    void createExpense_withDelegation_sendsAllDelegationHeaders() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/expenses"))
            .willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("amount", 150);
        client.createExpense(body, DELEGATION_TOKEN, Optional.of(buildDelegationContext()));

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/expenses"))
            .withHeader("Authorization", equalTo("Bearer " + DELEGATION_TOKEN))
            .withHeader("X-Delegated-Subject", equalTo(SUBJECT_ID))
            .withHeader("X-Delegation-Id", equalTo(DELEGATION_ID))
            .withHeader("X-Actor-Token", equalTo(ACTOR_TOKEN)));
    }

    // ── getExpense ────────────────────────────────────────────────────────────

    @Test
    void getExpense_withoutDelegation_sendsOnlyAuthorizationHeader() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/expenses/expense-1"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        client.getExpense("expense-1", BEARER_TOKEN, Optional.empty());

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/expenses/expense-1"))
            .withHeader("Authorization", equalTo("Bearer " + BEARER_TOKEN))
            .withoutHeader("X-Delegated-Subject")
            .withoutHeader("X-Delegation-Id")
            .withoutHeader("X-Actor-Token"));
    }

    @Test
    void getExpense_withDelegation_sendsAllDelegationHeaders() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/expenses/expense-1"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));

        client.getExpense("expense-1", DELEGATION_TOKEN, Optional.of(buildDelegationContext()));

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/expenses/expense-1"))
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
            .audience("expense-service")
            .delegationToken(DELEGATION_TOKEN)
            .actorToken(ACTOR_TOKEN)
            .consentId("consent-uuid-abc")
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    }
}
