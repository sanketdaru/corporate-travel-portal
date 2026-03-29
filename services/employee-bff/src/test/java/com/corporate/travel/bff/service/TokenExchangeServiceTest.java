package com.corporate.travel.bff.service;

import com.corporate.travel.bff.client.ConsentServiceClient;
import com.corporate.travel.bff.client.DelegationServiceClient;
import com.corporate.travel.bff.client.KeycloakTokenExchangeClient;
import com.corporate.travel.bff.exception.DelegationNotFoundException;
import com.corporate.travel.bff.exception.TokenExchangeException;
import com.corporate.travel.bff.model.DelegationContext;
import com.corporate.travel.bff.model.TokenExchangeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenExchangeServiceTest {

    @Mock
    private DelegationServiceClient delegationServiceClient;
    @Mock
    private ConsentServiceClient consentServiceClient;
    @Mock
    private KeycloakTokenExchangeClient keycloakTokenExchangeClient;

    private TokenExchangeService tokenExchangeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tokenExchangeService = new TokenExchangeService(
            delegationServiceClient, consentServiceClient, keycloakTokenExchangeClient);
    }

    @Test
    void exchangeForDelegation_happyPath_returnsDelegationContext() {
        ObjectNode delegationNode = objectMapper.createObjectNode();
        delegationNode.put("id", "delegation-123");
        delegationNode.put("delegatorId", "carol-user-id");
        delegationNode.put("delegateId", "dave-user-id");

        when(delegationServiceClient.getDelegation("delegation-123", "dave-token"))
            .thenReturn(delegationNode);
        when(consentServiceClient.hasConsentForScopes("carol-user-id", "dave-user-id", List.of("book_travel"), "dave-token"))
            .thenReturn(true);

        TokenExchangeResponse exchangeResponse = new TokenExchangeResponse();
        exchangeResponse.setAccessToken("delegation-token-abc");
        exchangeResponse.setExpiresIn(300L);

        when(keycloakTokenExchangeClient.exchangeToken("dave-token", "carol-user-id", "travel-service"))
            .thenReturn(exchangeResponse);

        DelegationContext result = tokenExchangeService.exchangeForDelegation(
            "delegation-123", "dave-token", "dave-user-id", "travel-service");

        assertThat(result.getDelegationId()).isEqualTo("delegation-123");
        assertThat(result.getActorId()).isEqualTo("dave-user-id");
        assertThat(result.getSubjectId()).isEqualTo("carol-user-id");
        assertThat(result.getAudience()).isEqualTo("travel-service");
        assertThat(result.getDelegationToken()).isEqualTo("delegation-token-abc");
        assertThat(result.getExpiresAt()).isNotNull();
    }

    @Test
    void exchangeForDelegation_delegationNotFound_throwsDelegationNotFoundException() {
        when(delegationServiceClient.getDelegation(eq("missing-id"), anyString()))
            .thenReturn(null);

        assertThatThrownBy(() -> tokenExchangeService.exchangeForDelegation(
            "missing-id", "dave-token", "dave-id", "travel-service"))
            .isInstanceOf(DelegationNotFoundException.class)
            .hasMessageContaining("missing-id");

        verifyNoInteractions(consentServiceClient, keycloakTokenExchangeClient);
    }

    @Test
    void exchangeForDelegation_noConsent_throwsTokenExchangeException() {
        ObjectNode delegationNode = objectMapper.createObjectNode();
        delegationNode.put("delegatorId", "carol-user-id");

        when(delegationServiceClient.getDelegation("delegation-123", "dave-token"))
            .thenReturn(delegationNode);
        when(consentServiceClient.hasConsentForScopes(anyString(), anyString(), anyList(), anyString()))
            .thenReturn(false);

        assertThatThrownBy(() -> tokenExchangeService.exchangeForDelegation(
            "delegation-123", "dave-token", "dave-user-id", "travel-service"))
            .isInstanceOf(TokenExchangeException.class)
            .hasMessageContaining("No active consent");

        verifyNoInteractions(keycloakTokenExchangeClient);
    }

    @Test
    void exchangeForDelegation_keycloakFails_propagatesTokenExchangeException() {
        ObjectNode delegationNode = objectMapper.createObjectNode();
        delegationNode.put("delegatorId", "carol-user-id");

        when(delegationServiceClient.getDelegation("delegation-123", "dave-token"))
            .thenReturn(delegationNode);
        when(consentServiceClient.hasConsentForScopes(anyString(), anyString(), anyList(), anyString()))
            .thenReturn(true);
        when(keycloakTokenExchangeClient.exchangeToken(anyString(), anyString(), anyString()))
            .thenThrow(new TokenExchangeException("Token exchange rejected by Keycloak"));

        assertThatThrownBy(() -> tokenExchangeService.exchangeForDelegation(
            "delegation-123", "dave-token", "dave-user-id", "travel-service"))
            .isInstanceOf(TokenExchangeException.class)
            .hasMessageContaining("Token exchange rejected by Keycloak");
    }
}
