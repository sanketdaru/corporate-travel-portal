package com.corporate.travel.bff.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelegationContextTest {

    private final DelegationContext context = DelegationContext.builder()
        .delegationId("delegation-123")
        .actorId("dave.assistant")
        .subjectId("carol.executive")
        .audiences(List.of("travel-service", "expense-service"))
        .delegationTokens(Map.of("travel-service", "travel-token", "expense-service", "expense-token"))
        .actorToken("dave-token")
        .expiresAt(Instant.now().plusSeconds(300))
        .build();

    @Test
    void tokenFor_returnsTokenScopedToAudience() {
        assertThat(context.tokenFor("travel-service")).isEqualTo("travel-token");
        assertThat(context.tokenFor("expense-service")).isEqualTo("expense-token");
    }

    @Test
    void tokenFor_unknownAudience_failsLoudly() {
        assertThatThrownBy(() -> context.tokenFor("consent-service"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("consent-service");
    }

    @Test
    void json_neverExposesTokens() {
        String json = JsonMapper.builder().build().writeValueAsString(context);

        assertThat(json).contains("carol.executive", "travel-service");
        assertThat(json).doesNotContain("travel-token", "expense-token", "dave-token", "delegationTokens", "actorToken");
    }
}
