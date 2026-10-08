package com.corporate.travel.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts JWT token to Spring Security authentication with custom claims
 */
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // A delegated token (act claim) carries the delegator's roles; the actor must not gain them.
        // Authorization of delegated requests uses the verified actor (see extractSecurityContext).
        Collection<GrantedAuthority> authorities = extractActorId(jwt) != null
                ? Collections.emptyList()
                : extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Extract realm roles from Keycloak token
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public static SecurityContext extractSecurityContext(Jwt jwt) {
        // Prefer preferred_username as the stable user identifier; fall back to sub (UUID)
        String userId = jwt.getClaimAsString("preferred_username") != null
                ? jwt.getClaimAsString("preferred_username")
                : jwt.getSubject();

        SecurityContext.SecurityContextBuilder builder = SecurityContext.builder()
                .userId(userId)
                .username(jwt.getClaimAsString("preferred_username"))
                .tenantId(jwt.getClaimAsString("tenant_id"))
                .roles(extractRoles(jwt));

        // RFC 8693 §4.1: "act" is a JSON object identifying the current actor; the token's own
        // subject is the party being acted for. Nested "act" members are prior actors in the chain.
        String actorId = extractActorId(jwt);
        if (actorId != null) {
            builder.isDelegated(true)
                   .actorId(actorId)
                   .subjectId(userId);
        } else {
            builder.isDelegated(false)
                   .actorId(userId)
                   .subjectId(userId);
        }

        // Extract consent and purpose claims
        builder.consentId(jwt.getClaimAsString("consent_id"))
               .purpose(jwt.getClaimAsString("purpose"));

        // Extract custom attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("employee_id", jwt.getClaimAsString("employee_id"));
        attributes.put("email", jwt.getClaimAsString("email"));
        builder.attributes(attributes);

        return builder.build();
    }

    /**
     * Builds the SecurityContext of a request, resolving RFC 8693 delegation (ADR-024).
     *
     * <p>A request is delegated only when its token carries an {@code act} claim and
     * {@link DelegatedActorFilter} has verified the actor's own token ({@code X-Actor-Token}).
     * Then the context describes the <em>actor</em> — user id, roles and attributes come from the
     * verified actor token, so delegation never grants the delegator's roles — and
     * {@code subjectId} is the delegator from the token's own claims. Audit metadata
     * ({@code X-Delegation-Id}, {@code X-Consent-Id}, {@code X-Delegation-Purpose}) is read from
     * headers. Without {@code act}, headers never make a request delegated.</p>
     *
     * @param jwt     The authenticated JWT (a delegated token: sub = delegator)
     * @param request The incoming HTTP request
     * @return SecurityContext for authorization (OPA) and audit
     */
    public static SecurityContext extractSecurityContext(Jwt jwt, HttpServletRequest request) {
        if (extractActorId(jwt) == null) {
            return extractSecurityContext(jwt);
        }
        if (!(request.getAttribute(DelegatedActorFilter.ACTOR_ATTRIBUTE) instanceof Jwt actor)) {
            throw new AccessDeniedException("Delegated token whose actor was not verified");
        }

        SecurityContext actorContext = extractSecurityContext(actor);
        String delegator = jwt.getClaimAsString("preferred_username") != null
                ? jwt.getClaimAsString("preferred_username")
                : jwt.getSubject();

        return SecurityContext.builder()
                .userId(actorContext.getUserId())       // actor (Dave) — identity used by OPA
                .username(actorContext.getUsername())
                .tenantId(actorContext.getTenantId())
                .roles(actorContext.getRoles())         // actor's roles, not the delegator's
                .attributes(actorContext.getAttributes())
                .isDelegated(true)
                .actorId(actorContext.getUserId())
                .subjectId(delegator)                   // delegator (Carol) — from the signed token
                .delegationId(request.getHeader("X-Delegation-Id"))
                .consentId(request.getHeader("X-Consent-Id"))
                .purpose(request.getHeader("X-Delegation-Purpose"))
                .build();
    }

    /**
     * Returns the current actor from the RFC 8693 {@code act} claim, or {@code null} if the token
     * is not a delegation token. Prefers {@code preferred_username} (the identifier used across
     * this platform) and falls back to {@code sub}.
     */
    static String extractActorId(Jwt jwt) {
        Object act = jwt.getClaim("act");
        if (!(act instanceof Map<?, ?> actClaim)) {
            return null;
        }
        Object username = actClaim.get("preferred_username");
        Object sub = actClaim.get("sub");
        Object actor = username != null ? username : sub;
        return actor != null && StringUtils.hasText(actor.toString()) ? actor.toString() : null;
    }

    private static List<String> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            return roles;
        }
        return Collections.emptyList();
    }
}
