package com.corporate.travel.bff.client;

import com.corporate.travel.bff.model.DelegationContext;
import com.corporate.travel.security.InternalHttpClientConfig;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

        RestClient restClient = RestClient.builder()
            .requestFactory(InternalHttpClientConfig.http11().build())
            .baseUrl("http://localhost:" + wireMockServer.port())
            .build();

        client = new ExpenseServiceClient(restClient);
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

        client.getExpenses(BEARER_TOKEN, Optional.of(buildDelegationContext()));

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/expenses"))
            .withHeader("Authorization", equalTo("Bearer " + DELEGATION_TOKEN))
            .withoutHeader("X-Delegated-Subject")
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
        client.createExpense(body, BEARER_TOKEN, Optional.of(buildDelegationContext()));

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/expenses"))
            .withHeader("Authorization", equalTo("Bearer " + DELEGATION_TOKEN))
            .withoutHeader("X-Delegated-Subject")
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

        client.getExpense("expense-1", BEARER_TOKEN, Optional.of(buildDelegationContext()));

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/expenses/expense-1"))
            .withHeader("Authorization", equalTo("Bearer " + DELEGATION_TOKEN))
            .withoutHeader("X-Delegated-Subject")
            .withHeader("X-Delegation-Id", equalTo(DELEGATION_ID))
            .withHeader("X-Actor-Token", equalTo(ACTOR_TOKEN)));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private DelegationContext buildDelegationContext() {
        return DelegationContext.builder()
            .delegationId(DELEGATION_ID)
            .actorId("dave-user-id")
            .subjectId(SUBJECT_ID)
            .audiences(List.of("travel-service", "expense-service"))
            // A token for the other service too: the client must pick the one for its own audience
            .delegationTokens(Map.of("expense-service", DELEGATION_TOKEN, "travel-service", "token-for-travel-service"))
            .actorToken(ACTOR_TOKEN)
            .consentId("consent-uuid-abc")
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    }
}
