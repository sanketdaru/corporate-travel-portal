package com.corporate.travel.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtAuthenticationConverter — RFC 8693 act claim")
class JwtAuthenticationConverterTest {

    @Test
    @DisplayName("should_notBeDelegated_when_noActClaim")
    void should_notBeDelegated_when_noActClaim() {
        SecurityContext ctx = JwtAuthenticationConverter.extractSecurityContext(jwt(Map.of()));

        assertThat(ctx.isDelegated()).isFalse();
        assertThat(ctx.getActorId()).isEqualTo("carol.executive");
        assertThat(ctx.getSubjectId()).isEqualTo("carol.executive");
    }

    @Test
    @DisplayName("should_useActSubAsActor_when_actIsObject")
    void should_useActSubAsActor_when_actIsObject() {
        SecurityContext ctx = JwtAuthenticationConverter.extractSecurityContext(
            jwt(Map.of("act", Map.of("sub", "dave-uuid"))));

        assertThat(ctx.isDelegated()).isTrue();
        assertThat(ctx.getActorId()).isEqualTo("dave-uuid");
        assertThat(ctx.getSubjectId()).isEqualTo("carol.executive");
    }

    @Test
    @DisplayName("should_preferActPreferredUsername_when_present")
    void should_preferActPreferredUsername_when_present() {
        SecurityContext ctx = JwtAuthenticationConverter.extractSecurityContext(
            jwt(Map.of("act", Map.of("sub", "dave-uuid", "preferred_username", "dave.assistant"))));

        assertThat(ctx.getActorId()).isEqualTo("dave.assistant");
    }

    @Test
    @DisplayName("should_useCurrentActor_when_actIsNested")
    void should_useCurrentActor_when_actIsNested() {
        // RFC 8693 §4.1: the outermost act is the current actor; nested act members are prior actors
        SecurityContext ctx = JwtAuthenticationConverter.extractSecurityContext(
            jwt(Map.of("act", Map.of("sub", "agent-bot", "act", Map.of("sub", "dave-uuid")))));

        assertThat(ctx.getActorId()).isEqualTo("agent-bot");
    }

    @Test
    @DisplayName("should_ignoreAct_when_notAnObject")
    void should_ignoreAct_when_notAnObject() {
        SecurityContext ctx = JwtAuthenticationConverter.extractSecurityContext(
            jwt(Map.of("act", "dave-uuid")));

        assertThat(ctx.isDelegated()).isFalse();
    }

    private static Jwt jwt(Map<String, Object> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("carol-uuid")
            .claim("preferred_username", "carol.executive")
            .claim("tenant_id", "tenant-a")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300));
        extraClaims.forEach(builder::claim);
        return builder.build();
    }
}
