package com.corporate.travel.expense.client;

import com.corporate.travel.expense.config.TravelServiceClientConfig;
import com.corporate.travel.expense.exception.LinkedBookingNotFoundException;
import com.corporate.travel.expense.exception.TravelServiceUnavailableException;
import com.corporate.travel.security.InternalHttpClientConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the budget call carries the expense-service client-credentials token
 * (ADR-009), passes the tenant, and fails closed.
 */
class TravelServiceClientTest {

    private static final String TOKEN_PATH = "/realms/corporate-travel/protocol/openid-connect/token";
    private static final String TENANT = "tenant-a";
    private static final UUID BOOKING_ID = UUID.fromString("b1111111-1111-1111-1111-111111111111");
    private static final String BUDGET_PATH = "/api/bookings/" + BOOKING_ID + "/budget";

    private WireMockServer wireMockServer;
    private TravelServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        String baseUrl = "http://localhost:" + wireMockServer.port();

        ClientRegistration registration = ClientRegistration.withRegistrationId(TravelServiceClientConfig.REGISTRATION_ID)
            .clientId("expense-service")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(baseUrl + TOKEN_PATH)
            .build();
        InMemoryClientRegistrationRepository registrations = new InMemoryClientRegistrationRepository(registration);
        OAuth2AuthorizedClientManager manager = TravelServiceClientConfig.authorizedClientManager(
            registrations, new InMemoryOAuth2AuthorizedClientService(registrations), InternalHttpClientConfig.http11());

        RestClient restClient = RestClient.builder()
            .requestFactory(InternalHttpClientConfig.http11().build())
            .baseUrl(baseUrl)
            .requestInterceptor(TravelServiceClientConfig.serviceAccountInterceptor(manager))
            .build();
        client = new TravelServiceClient(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private void stubToken() {
        wireMockServer.stubFor(post(urlPathEqualTo(TOKEN_PATH))
            .withRequestBody(containing("grant_type=client_credentials"))
            .willReturn(okJson("{\"access_token\":\"svc-token\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
    }

    @Test
    void getBookingBudget_sendsServiceAccountTokenAndTenant() {
        stubToken();
        wireMockServer.stubFor(get(urlPathEqualTo(BUDGET_PATH))
            .willReturn(okJson("{\"id\":\"" + BOOKING_ID + "\",\"budget\":1500.00,\"budgetCurrency\":\"USD\"}")));

        var budget = client.getBookingBudget(BOOKING_ID, TENANT);

        assertThat(budget.budget()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(budget.budgetCurrency()).isEqualTo("USD");
        wireMockServer.verify(getRequestedFor(urlPathEqualTo(BUDGET_PATH))
            .withQueryParam("tenantId", equalTo(TENANT))
            .withHeader("Authorization", equalTo("Bearer svc-token")));
    }

    @Test
    void getBookingBudget_reusesTokenAcrossCalls() {
        stubToken();
        wireMockServer.stubFor(get(urlPathEqualTo(BUDGET_PATH))
            .willReturn(okJson("{\"budget\":10,\"budgetCurrency\":\"INR\"}")));

        client.getBookingBudget(BOOKING_ID, TENANT);
        client.getBookingBudget(BOOKING_ID, TENANT);

        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
        wireMockServer.verify(2, getRequestedFor(urlPathEqualTo(BUDGET_PATH)));
    }

    @Test
    void getBookingBudget_bookingNotInTenant_failsClosed() {
        stubToken();
        wireMockServer.stubFor(get(urlPathEqualTo(BUDGET_PATH)).willReturn(notFound()));

        assertThatThrownBy(() -> client.getBookingBudget(BOOKING_ID, TENANT))
            .isInstanceOf(LinkedBookingNotFoundException.class);
    }

    @Test
    void getBookingBudget_travelServiceError_failsClosed() {
        stubToken();
        wireMockServer.stubFor(get(urlPathEqualTo(BUDGET_PATH)).willReturn(serverError()));

        assertThatThrownBy(() -> client.getBookingBudget(BOOKING_ID, TENANT))
            .isInstanceOf(TravelServiceUnavailableException.class);
    }

    @Test
    void getBookingBudget_tokenRejectedByTravelService_failsClosed() {
        stubToken();
        wireMockServer.stubFor(get(urlPathEqualTo(BUDGET_PATH)).willReturn(forbidden()));

        assertThatThrownBy(() -> client.getBookingBudget(BOOKING_ID, TENANT))
            .isInstanceOf(TravelServiceUnavailableException.class);
    }

    @Test
    void getBookingBudget_tokenEndpointRejectsClient_failsClosedWithoutCallingTravelService() {
        wireMockServer.stubFor(post(urlPathEqualTo(TOKEN_PATH))
            .willReturn(aResponse().withStatus(401).withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"invalid_client\"}")));

        assertThatThrownBy(() -> client.getBookingBudget(BOOKING_ID, TENANT))
            .isInstanceOf(TravelServiceUnavailableException.class);
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo(BUDGET_PATH)));
    }

    @Test
    void getBookingBudget_responseWithoutBudget_failsClosed() {
        stubToken();
        wireMockServer.stubFor(get(urlPathEqualTo(BUDGET_PATH)).willReturn(okJson("{}")));

        assertThatThrownBy(() -> client.getBookingBudget(BOOKING_ID, TENANT))
            .isInstanceOf(TravelServiceUnavailableException.class);
    }
}
