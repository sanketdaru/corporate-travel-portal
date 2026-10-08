package com.corporate.travel.bff.client;

import com.corporate.travel.bff.config.BffProperties;
import com.corporate.travel.bff.exception.TokenExchangeException;
import com.corporate.travel.bff.model.TokenExchangeResponse;
import com.corporate.travel.security.InternalHttpClientConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        client = new KeycloakTokenExchangeClient(properties, InternalHttpClientConfig.http11());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void exchangeToken_success_returnsAudienceScopedToken() {
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

        TokenExchangeResponse response = client.exchangeDelegated("carol-subject-token", "dave-actor-token", "travel-service");

        assertThat(response.getAccessToken()).isEqualTo("delegation-token-xyz");
        assertThat(response.getExpiresIn()).isEqualTo(300L);

        // RFC 8693 delegation (ADR-024): delegator as subject_token, actor as actor_token
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/realms/corporate-travel/protocol/openid-connect/token"))
            .withRequestBody(containing("subject_token=carol-subject-token"))
            .withRequestBody(containing("actor_token=dave-actor-token"))
            .withRequestBody(containing("actor_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aaccess_token"))
            .withRequestBody(containing("audience=travel-service"))
            .withRequestBody(containing("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange"))
            // Keycloak Standard V2 only accepts access tokens as subject_token
            .withRequestBody(containing("subject_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aaccess_token"))
            .withRequestBody(containing("client_id=employee-bff"))
            .withRequestBody(containing("client_secret=test-secret"))
            .withRequestBody(notContaining("requested_subject")));
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

        assertThatThrownBy(() -> client.exchangeDelegated("carol-token", "dave-token", "travel-service"))
            .isInstanceOf(TokenExchangeException.class)
            .hasMessageContaining("Token exchange rejected by Keycloak");
    }

    @Test
    void exchangeDelegated_missingActorToken_throwsBeforeCallingKeycloak() {
        assertThatThrownBy(() -> client.exchangeDelegated("carol-token", "", "travel-service"))
            .isInstanceOf(TokenExchangeException.class)
            .hasMessageContaining("are both mandatory");

        wireMockServer.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void exchangeDelegated_missingSubjectToken_throwsBeforeCallingKeycloak() {
        assertThatThrownBy(() -> client.exchangeDelegated(null, "dave-token", "travel-service"))
            .isInstanceOf(TokenExchangeException.class)
            .hasMessageContaining("are both mandatory");

        wireMockServer.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void exchangeToken_keycloakServerError_throwsTokenExchangeException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/realms/corporate-travel/protocol/openid-connect/token"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"server_error\"}")));

        assertThatThrownBy(() -> client.exchangeDelegated("carol-token", "dave-token", "travel-service"))
            .isInstanceOf(TokenExchangeException.class)
            .hasMessageContaining("Keycloak server error");
    }
}
