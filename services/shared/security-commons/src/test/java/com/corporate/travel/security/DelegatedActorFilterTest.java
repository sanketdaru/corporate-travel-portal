package com.corporate.travel.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RFC 8693 delegated requests (ADR-024)")
class DelegatedActorFilterTest {

    private static final String DAVE_SUB = "dave-uuid";

    private final Jwt daveToken = jwt("dave-token", DAVE_SUB, "dave.assistant", "tenant-a", List.of("employee", "assistant"), Map.of());
    private final Jwt carolDelegated = jwt("delegated", "carol-uuid", "carol.executive", "tenant-a",
        List.of("employee", "executive"), Map.of("act", Map.of("sub", DAVE_SUB)));

    private final JwtDecoder decoder = token -> switch (token) {
        case "dave-token" -> daveToken;
        case "eve-token" -> jwt("eve-token", "eve-uuid", "eve.employee", "tenant-b", List.of("employee"), Map.of());
        case "dave-tenant-b" -> jwt("dave-tenant-b", DAVE_SUB, "dave.assistant", "tenant-b", List.of("employee"), Map.of());
        default -> throw new BadJwtException("bad signature");
    };
    private final DelegatedActorFilter filter = new DelegatedActorFilter(decoder);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegatedToken_withVerifiedActorToken_actsAsActorForSubject() throws Exception {
        MockHttpServletRequest request = request(carolDelegated, "dave-token");
        request.addHeader("X-Delegation-Id", "delegation-1");
        MockHttpServletResponse response = run(request);

        assertThat(response.getStatus()).isEqualTo(200);
        SecurityContext ctx = JwtAuthenticationConverter.extractSecurityContext(carolDelegated, request);
        assertThat(ctx.isDelegated()).isTrue();
        assertThat(ctx.getUserId()).isEqualTo("dave.assistant");
        assertThat(ctx.getActorId()).isEqualTo("dave.assistant");
        assertThat(ctx.getSubjectId()).isEqualTo("carol.executive");
        // The actor's roles — delegation must not hand Dave Carol's "executive" role
        assertThat(ctx.getRoles()).containsExactly("employee", "assistant");
        assertThat(ctx.getDelegationId()).isEqualTo("delegation-1");
    }

    @Test
    void delegatedToken_withoutActorToken_isRejected() throws Exception {
        assertThat(run(request(carolDelegated, null)).getStatus()).isEqualTo(401);
    }

    @Test
    void delegatedToken_withForgedActorToken_isRejected() throws Exception {
        assertThat(run(request(carolDelegated, "forged")).getStatus()).isEqualTo(401);
    }

    @Test
    void delegatedToken_withSomeoneElsesActorToken_isRejected() throws Exception {
        assertThat(run(request(carolDelegated, "eve-token")).getStatus()).isEqualTo(401);
    }

    @Test
    void delegatedToken_actorInOtherTenant_isRejected() throws Exception {
        assertThat(run(request(carolDelegated, "dave-tenant-b")).getStatus()).isEqualTo(401);
    }

    @Test
    void delegatedToken_actorNotVerified_isDeniedWhenBuildingContext() {
        MockHttpServletRequest request = request(carolDelegated, "dave-token");   // filter not run

        assertThatThrownBy(() -> JwtAuthenticationConverter.extractSecurityContext(carolDelegated, request))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void plainToken_withDelegatedSubjectHeader_isNotDelegated() throws Exception {
        // The pre-ADR-024 header no longer makes a request delegated
        MockHttpServletRequest request = request(daveToken, null);
        request.addHeader("X-Delegated-Subject", "carol.executive");
        assertThat(run(request).getStatus()).isEqualTo(200);

        SecurityContext ctx = JwtAuthenticationConverter.extractSecurityContext(daveToken, request);
        assertThat(ctx.isDelegated()).isFalse();
        assertThat(ctx.getSubjectId()).isEqualTo("dave.assistant");
    }

    @Test
    void delegatedToken_grantsNoSpringAuthorities() {
        assertThat(new JwtAuthenticationConverter().convert(carolDelegated).getAuthorities()).isEmpty();
        assertThat(new JwtAuthenticationConverter().convert(daveToken).getAuthorities()).isNotEmpty();
    }

    private MockHttpServletRequest request(Jwt authenticated, String actorToken) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(authenticated));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bookings");
        if (actorToken != null) {
            request.addHeader("X-Actor-Token", actorToken);
        }
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static Jwt jwt(String value, String sub, String username, String tenant, List<String> roles, Map<String, Object> extra) {
        Jwt.Builder builder = Jwt.withTokenValue(value).header("alg", "RS256")
            .subject(sub).claim("preferred_username", username).claim("tenant_id", tenant)
            .claim("realm_access", Map.of("roles", roles))
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        extra.forEach(builder::claim);
        return builder.build();
    }
}
