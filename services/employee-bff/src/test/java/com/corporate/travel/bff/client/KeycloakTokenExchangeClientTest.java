package com.corporate.travel.bff.client;

import com.corporate.travel.bff.config.BffProperties;
import com.corporate.travel.bff.exception.TokenExchangeException;
import com.corporate.travel.bff.model.TokenExchangeResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakTokenExchangeClientTest {

    private WireMockServer wireMockServer;
    private KeycloakTokenExchangeClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        BffProperties properties = new BffProperties();
        BffProperties.Keycloak keycloak = new BffProperties.Keycloak();
        keycloak.setUrl("http://localhost:" + wireMockServer.port());
        keycloak.setRealm("corporate-travel");
        keycloak.setClientId("employee-bff");
        keycloak.setClientSecret("test-secret");
        properties.setKeycloak(keycloak);

        WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:" + wireMockServer.port())
            .build();

        client = new KeycloakTokenExchangeClient(webClient, properties);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void exchangeToken_success_returnsDelegationToken() {
        wireMockServer.stubFor(post(urlPathEqualTo("/realms/corporate-travel/protocol/openid-connect/token"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "access_token": "delegation-token-xyz",
                      "token_type": "Bearer",
                      "expires_in": 300
                    }
                    """)));

        TokenExchangeResponse response = client.exchangeToken("dave-actor-token", "carol-user-id", "travel-service");

        assertThat(response.getAccessToken()).isEqualTo("delegation-token-xyz");
        assertThat(response.getExpiresIn()).isEqualTo(300L);

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/realms/corporate-travel/protocol/openid-connect/token"))
            .withRequestBody(containing("subject_token=dave-actor-token"))
            .withRequestBody(containing("requested_subject=carol-user-id"))
            .withRequestBody(containing("audience=travel-service"))
            .withRequestBody(containing("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange")));
    }

    @Test
    void exchangeToken_keycloakRejects_throwsTokenExchangeException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/realms/corporate-travel/protocol/openid-connect/token"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"error":"invalid_request","error_description":"subject_token is required"}
                    """)));

        assertThatThrownBy(() -> client.exchangeToken("dave-token", "carol-id", "travel-service"))
            .isInstanceOf(TokenExchangeException.class)
            .hasMessageContaining("Token exchange rejected by Keycloak");
    }

    @Test
    void exchangeToken_missingActorToken_throwsTokenExchangeExceptionBeforeCallingKeycloak() {
        assertThatThrownBy(() -> client.exchangeToken("", "carol-id", "travel-service"))
            .isInstanceOf(TokenExchangeException.class)
            .hasMessageContaining("subject_token (actorToken) is mandatory");

        wireMockServer.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void exchangeToken_nullActorToken_throwsTokenExchangeExceptionBeforeCallingKeycloak() {
        assertThatThrownBy(() -> client.exchangeToken(null, "carol-id", "travel-service"))
            .isInstanceOf(TokenExchangeException.class)
            .hasMessageContaining("subject_token (actorToken) is mandatory");

        wireMockServer.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void exchangeToken_keycloakServerError_throwsTokenExchangeException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/realms/corporate-travel/protocol/openid-connect/token"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"server_error\"}")));

        assertThatThrownBy(() -> client.exchangeToken("dave-token", "carol-id", "travel-service"))
            .isInstanceOf(TokenExchangeException.class)
            .hasMessageContaining("Keycloak server error");
    }
}
