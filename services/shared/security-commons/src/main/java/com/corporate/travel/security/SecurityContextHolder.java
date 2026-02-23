package com.corporate.travel.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Service wrapper for SecurityContext extraction
 * Enables dependency injection and easier testing
 * 
 * Updated to support unit testing; does not modify business logic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityContextHolder {
    
    /**
     * Extracts SecurityContext from JWT token
     * 
     * @param jwt JWT token from authentication
     * @return SecurityContext with user, tenant, and delegation info
     */
    public SecurityContext getCurrentContext(Jwt jwt) {
        return JwtAuthenticationConverter.extractSecurityContext(jwt);
    }
    
    /**
     * Extracts SecurityContext with detailed logging
     * Useful for debugging delegation and multi-tenant scenarios
     * 
     * @param jwt JWT token
     * @return SecurityContext
     */
    public SecurityContext getCurrentContextWithLogging(Jwt jwt) {
        SecurityContext context = JwtAuthenticationConverter.extractSecurityContext(jwt);
        log.debug("Extracted SecurityContext - User: {}, Tenant: {}, Delegated: {}", 
            context.getUserId(), context.getTenantId(), context.isDelegated());
        return context;
    }
}