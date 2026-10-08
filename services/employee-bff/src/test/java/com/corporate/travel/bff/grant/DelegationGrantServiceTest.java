package com.corporate.travel.bff.grant;

import com.corporate.travel.bff.client.DelegationServiceClient;
import com.corporate.travel.bff.config.BffProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DelegationGrantServiceTest {

    private static final String DELEGATION_ID = "7f3c2a10-1111-4222-8333-444455556666";

    @Mock private DelegationServiceClient delegationServiceClient;
    @Mock private KeycloakGrantClient keycloakGrantClient;
    @Mock private DelegationGrantRepository repository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private GrantTokenCipher cipher;
    private DelegationGrantService service;
    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void setUp() {
        BffProperties properties = new BffProperties();
        properties.getDelegation().getGrant().setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        properties.getDelegation().getGrant().setReturnUrl("http://localhost:3000/delegation");
        cipher = new GrantTokenCipher(properties);
        service = new DelegationGrantService(delegationServiceClient, keycloakGrantClient, repository, cipher,
            objectMapper, properties);
    }

    @Test
    void startGrant_byDelegator_returnsAuthorizationUrlWithDelegateScope() {
        when(delegationServiceClient.getDelegation(DELEGATION_ID, "carol-token")).thenReturn(delegation(true));
        when(keycloakGrantClient.authorizationUrl(eq("dave.assistant"), eq("carol.executive"), anyString(), anyString()))
            .thenReturn("http://keycloak/auth?...");

        String url = service.startGrant(DELEGATION_ID, "carol.executive", "carol-token", session);

        assertThat(url).isEqualTo("http://keycloak/auth?...");
        assertThat(session.getAttribute(DelegationGrantService.PENDING_GRANTS)).isNotNull();
    }

    @Test
    void startGrant_byAnyoneElse_isDenied() {
        when(delegationServiceClient.getDelegation(DELEGATION_ID, "dave-token")).thenReturn(delegation(true));

        assertThatThrownBy(() -> service.startGrant(DELEGATION_ID, "dave.assistant", "dave-token", session))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(keycloakGrantClient);
    }

    @Test
    void completeGrant_storesEncryptedOfflineToken() {
        String state = begin();
        when(keycloakGrantClient.redeemCode(eq("code-1"), anyString()))
            .thenReturn(new KeycloakGrantClient.GrantTokens(jwt(Map.of("preferred_username", "carol.executive",
                "may_act", Map.of("sub", "dave-uuid"))), "offline-token"));

        String next = service.completeGrant("code-1", state, null, session);

        assertThat(next).contains("grant=success").contains("delegationId=" + DELEGATION_ID);
        ArgumentCaptor<DelegationGrantRepository.StoredGrant> saved = ArgumentCaptor.forClass(DelegationGrantRepository.StoredGrant.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().encryptedOfflineToken()).isNotEqualTo("offline-token");
        assertThat(cipher.decrypt(saved.getValue().encryptedOfflineToken(), DELEGATION_ID)).isEqualTo("offline-token");
    }

    @Test
    void completeGrant_withoutMayAct_isRejectedAndTokenRevoked() {
        // Keycloak drops the delegation scope when FGAP does not permit the pair
        String state = begin();
        when(keycloakGrantClient.redeemCode(eq("code-1"), anyString()))
            .thenReturn(new KeycloakGrantClient.GrantTokens(jwt(Map.of("preferred_username", "carol.executive")), "offline-token"));

        String next = service.completeGrant("code-1", state, null, session);

        assertThat(next).contains("grant=error").contains("reason=delegation_not_permitted");
        verify(keycloakGrantClient).revoke("offline-token");
        verify(repository, never()).save(any());
    }

    @Test
    void completeGrant_signedInAsSomeoneElse_isRejected() {
        String state = begin();
        when(keycloakGrantClient.redeemCode(eq("code-1"), anyString()))
            .thenReturn(new KeycloakGrantClient.GrantTokens(jwt(Map.of("preferred_username", "alice.employee",
                "may_act", Map.of("sub", "dave-uuid"))), "offline-token"));

        assertThat(service.completeGrant("code-1", state, null, session)).contains("reason=wrong_account");
        verify(repository, never()).save(any());
    }

    @Test
    void completeGrant_unknownState_isRejected() {
        assertThat(service.completeGrant("code-1", "forged-state", null, session)).contains("grant=error");
        verifyNoInteractions(keycloakGrantClient, repository);
    }

    @Test
    void delegatorAccessToken_refreshesStoredOfflineToken() {
        when(repository.find(UUID.fromString(DELEGATION_ID))).thenReturn(Optional.of(stored("offline-token")));
        when(keycloakGrantClient.refresh("offline-token")).thenReturn(new KeycloakGrantClient.GrantTokens("carol-access", null));

        assertThat(service.delegatorAccessToken(delegation(true))).isEqualTo("carol-access");
    }

    @Test
    void delegatorAccessToken_withoutGrant_isNotAuthorized() {
        when(repository.find(UUID.fromString(DELEGATION_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delegatorAccessToken(delegation(true)))
            .isInstanceOf(DelegationGrantService.DelegationNotAuthorizedException.class);
    }

    @Test
    void delegatorAccessToken_forInactiveDelegation_revokesAndDeletesGrant() {
        when(repository.find(UUID.fromString(DELEGATION_ID))).thenReturn(Optional.of(stored("offline-token")));

        assertThatThrownBy(() -> service.delegatorAccessToken(delegation(false)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(keycloakGrantClient).revoke("offline-token");
        verify(repository).delete(UUID.fromString(DELEGATION_ID));
    }

    @Test
    void delegatorAccessToken_offlineSessionGone_deletesGrant() {
        when(repository.find(UUID.fromString(DELEGATION_ID))).thenReturn(Optional.of(stored("offline-token")));
        when(keycloakGrantClient.refresh("offline-token"))
            .thenThrow(new KeycloakGrantClient.GrantRejectedException("invalid_grant", true));

        assertThatThrownBy(() -> service.delegatorAccessToken(delegation(true)))
            .isInstanceOf(DelegationGrantService.DelegationNotAuthorizedException.class);
        verify(repository).delete(UUID.fromString(DELEGATION_ID));
    }

    @Test
    void revoke_revokesDelegationAndKeycloakGrant() {
        when(repository.find(UUID.fromString(DELEGATION_ID))).thenReturn(Optional.of(stored("offline-token")));

        service.revoke(DELEGATION_ID, "carol-token");

        verify(delegationServiceClient).revokeDelegation(DELEGATION_ID, "carol-token");
        verify(keycloakGrantClient).revoke("offline-token");
        verify(repository).delete(UUID.fromString(DELEGATION_ID));
    }

    private String begin() {
        when(delegationServiceClient.getDelegation(DELEGATION_ID, "carol-token")).thenReturn(delegation(true));
        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        when(keycloakGrantClient.authorizationUrl(anyString(), anyString(), state.capture(), anyString())).thenReturn("url");
        service.startGrant(DELEGATION_ID, "carol.executive", "carol-token", session);
        return state.getValue();
    }

    private DelegationGrantRepository.StoredGrant stored(String offlineToken) {
        return new DelegationGrantRepository.StoredGrant(UUID.fromString(DELEGATION_ID), "carol.executive",
            "dave.assistant", cipher.encrypt(offlineToken, DELEGATION_ID));
    }

    private ObjectNode delegation(boolean active) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", DELEGATION_ID);
        node.put("delegatorId", "carol.executive");
        node.put("delegateId", "dave.assistant");
        node.put("active", active);
        node.put("valid", active);
        return node;
    }

    private String jwt(Map<String, Object> claims) {
        String payload = objectMapper.writeValueAsString(claims);
        return "eyJhbGciOiJub25lIn0." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }
}
