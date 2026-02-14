package com.corporate.travel.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

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
        SecurityContext.SecurityContextBuilder builder = SecurityContext.builder()
                .userId(jwt.getClaimAsString("preferred_username"))
                .username(jwt.getClaimAsString("preferred_username"))
                .tenantId(jwt.getClaimAsString("tenant_id"))
                .roles(extractRoles(jwt));

        // Check for delegation context (actor/subject pattern)
        String actToken = jwt.getClaimAsString("act");
        if (actToken != null) {
            builder.isDelegated(true)
                   .actorId(jwt.getClaimAsString("act_sub"))
                   .subjectId(jwt.getSubject());
        } else {
            builder.isDelegated(false)
                   .actorId(jwt.getSubject())
                   .subjectId(jwt.getSubject());
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
