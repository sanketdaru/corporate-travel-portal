package com.corporate.travel.bff.service;

import com.corporate.travel.bff.model.DelegationContext;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DelegationContextServiceTest {

    @Mock
    private TokenExchangeService tokenExchangeService;
    @Mock
    private HttpSession session;

    private DelegationContextService delegationContextService;

    @BeforeEach
    void setUp() {
        delegationContextService = new DelegationContextService(tokenExchangeService);
    }

    @Test
    void activateDelegation_storesContextInSession() {
        DelegationContext context = DelegationContext.builder()
            .delegationId("delegation-123")
            .actorId("dave-id")
            .subjectId("carol-id")
            .audience("travel-service")
            .delegationToken("delegation-token")
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

        when(tokenExchangeService.exchangeForDelegation("delegation-123", "dave-token", "dave-id", "travel-service"))
            .thenReturn(context);

        DelegationContext result = delegationContextService.activateDelegation(
            "delegation-123", "dave-token", "dave-id", "travel-service", session);

        assertThat(result).isEqualTo(context);
        verify(session).setAttribute(DelegationContextService.SESSION_KEY, context);
    }

    @Test
    void deactivateDelegation_removesContextFromSession() {
        DelegationContext context = DelegationContext.builder()
            .actorId("dave-id")
            .subjectId("carol-id")
            .build();
        when(session.getAttribute(DelegationContextService.SESSION_KEY)).thenReturn(context);

        delegationContextService.deactivateDelegation(session);

        verify(session).removeAttribute(DelegationContextService.SESSION_KEY);
    }

    @Test
    void deactivateDelegation_noActiveContext_doesNotThrow() {
        when(session.getAttribute(DelegationContextService.SESSION_KEY)).thenReturn(null);

        delegationContextService.deactivateDelegation(session);

        verify(session).removeAttribute(DelegationContextService.SESSION_KEY);
    }

    @Test
    void getActiveContext_validContext_returnsContext() {
        DelegationContext context = DelegationContext.builder()
            .delegationId("delegation-123")
            .actorId("dave-id")
            .subjectId("carol-id")
            .delegationToken("some-token")
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

        when(session.getAttribute(DelegationContextService.SESSION_KEY)).thenReturn(context);

        Optional<DelegationContext> result = delegationContextService.getActiveContext(session);

        assertThat(result).isPresent();
        assertThat(result.get().getActorId()).isEqualTo("dave-id");
    }

    @Test
    void getActiveContext_noContext_returnsEmpty() {
        when(session.getAttribute(DelegationContextService.SESSION_KEY)).thenReturn(null);

        Optional<DelegationContext> result = delegationContextService.getActiveContext(session);

        assertThat(result).isEmpty();
    }

    @Test
    void getActiveContext_expiredContext_removesFromSessionAndReturnsEmpty() {
        DelegationContext expiredContext = DelegationContext.builder()
            .delegationId("delegation-123")
            .actorId("dave-id")
            .subjectId("carol-id")
            .delegationToken("expired-token")
            .expiresAt(Instant.now().minusSeconds(60))
            .build();

        when(session.getAttribute(DelegationContextService.SESSION_KEY)).thenReturn(expiredContext);

        Optional<DelegationContext> result = delegationContextService.getActiveContext(session);

        assertThat(result).isEmpty();
        verify(session).removeAttribute(DelegationContextService.SESSION_KEY);
    }
}
