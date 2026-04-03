package com.corporate.travel.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.convert.converter.Converter;
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
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
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

        // Check for delegation context (actor/subject pattern)
        String actToken = jwt.getClaimAsString("act");
        if (actToken != null) {
            builder.isDelegated(true)
                   .actorId(jwt.getClaimAsString("act_sub"))
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
     * Builds a SecurityContext from the JWT, enriched with delegation headers from the HTTP request.
     *
     * <p>When the BFF threads delegation headers ({@code X-Delegated-Subject}, {@code X-Delegation-Id},
     * {@code X-Consent-Id}, {@code X-Delegation-Purpose}), this method uses them to populate the
     * delegation fields of the SecurityContext so that downstream OPA authorization can check
     * delegation and consent rules correctly (ADR-004, ADR-011).</p>
     *
     * @param jwt     The authenticated JWT (actor's audience-scoped token)
     * @param request The incoming HTTP request carrying optional delegation headers
     * @return SecurityContext populated from JWT claims and delegation headers
     */
    public static SecurityContext extractSecurityContext(Jwt jwt, HttpServletRequest request) {
        SecurityContext base = extractSecurityContext(jwt);

        String delegatedSubject = request.getHeader("X-Delegated-Subject");
        if (!StringUtils.hasText(delegatedSubject)) {
            return base;
        }

        // Delegation headers present — override delegation fields
        String consentId = request.getHeader("X-Consent-Id");
        String purpose   = request.getHeader("X-Delegation-Purpose");

        return SecurityContext.builder()
                .userId(base.getUserId())
                .username(base.getUsername())
                .tenantId(base.getTenantId())
                .roles(base.getRoles())
                .attributes(base.getAttributes())
                .isDelegated(true)
                .actorId(base.getUserId())   // actor = JWT bearer (Dave)
                .subjectId(delegatedSubject) // subject = Carol (from header)
                .consentId(consentId)
                .purpose(purpose)
                .build();
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
