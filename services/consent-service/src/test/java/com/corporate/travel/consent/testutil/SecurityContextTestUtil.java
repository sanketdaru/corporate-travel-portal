package com.corporate.travel.consent.testutil;

import com.corporate.travel.security.SecurityContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for creating SecurityContext instances in tests
 * Provides convenient factory methods for common security scenarios
 */
public class SecurityContextTestUtil {
    
    /**
     * Create a basic security context with user and tenant
     */
    public static SecurityContext createContext(String userId, String tenantId) {
        return SecurityContext.builder()
                .userId(userId)
                .username(userId)
                .tenantId(tenantId)
                .roles(List.of("employee"))
                .isDelegated(false)
                .actorId(userId)
                .subjectId(userId)
                .attributes(new HashMap<>())
                .build();
    }
    
    /**
     * Create a security context with specific roles
     */
    public static SecurityContext createContextWithRoles(String userId, String tenantId, List<String> roles) {
        return SecurityContext.builder()
                .userId(userId)
                .username(userId)
                .tenantId(tenantId)
                .roles(roles)
                .isDelegated(false)
                .actorId(userId)
                .subjectId(userId)
                .attributes(new HashMap<>())
                .build();
    }
    
    /**
     * Create a delegated security context (actor acting on behalf of subject)
     */
    public static SecurityContext createDelegatedContext(String actorId, String subjectId, String tenantId) {
        return SecurityContext.builder()
                .userId(actorId)  // Current user is the actor
                .username(actorId)
                .tenantId(tenantId)
                .roles(List.of("employee"))
                .isDelegated(true)
                .actorId(actorId)
                .subjectId(subjectId)
                .consentId("consent-123")
                .purpose("consent_management")
                .attributes(new HashMap<>())
                .build();
    }
    
    /**
     * Create a delegated context with consent information
     */
    public static SecurityContext createDelegatedContextWithConsent(
            String actorId, String subjectId, String tenantId, String consentId, String purpose) {
        return SecurityContext.builder()
                .userId(actorId)
                .username(actorId)
                .tenantId(tenantId)
                .roles(List.of("employee"))
                .isDelegated(true)
                .actorId(actorId)
                .subjectId(subjectId)
                .consentId(consentId)
                .purpose(purpose)
                .attributes(new HashMap<>())
                .build();
    }
    
    /**
     * Create a manager security context
     */
    public static SecurityContext createManagerContext(String userId, String tenantId) {
        return SecurityContext.builder()
                .userId(userId)
                .username(userId)
                .tenantId(tenantId)
                .roles(List.of("employee", "manager"))
                .isDelegated(false)
                .actorId(userId)
                .subjectId(userId)
                .attributes(new HashMap<>())
                .build();
    }
    
    /**
     * Create an admin security context
     */
    public static SecurityContext createAdminContext(String userId, String tenantId) {
        return SecurityContext.builder()
                .userId(userId)
                .username(userId)
                .tenantId(tenantId)
                .roles(List.of("employee", "admin"))
                .isDelegated(false)
                .actorId(userId)
                .subjectId(userId)
                .attributes(new HashMap<>())
                .build();
    }
    
    /**
     * Create a context for Alice in Tenant A
     */
    public static SecurityContext aliceContext() {
        return createContext(ConsentTestFixtures.ALICE_USER_ID, ConsentTestFixtures.TENANT_A);
    }
    
    /**
     * Create a context for Bob (manager) in Tenant A
     */
    public static SecurityContext bobContext() {
        return createManagerContext(ConsentTestFixtures.BOB_USER_ID, ConsentTestFixtures.TENANT_A);
    }
    
    /**
     * Create a context for Carol (executive) in Tenant A
     */
    public static SecurityContext carolContext() {
        return createContext(ConsentTestFixtures.CAROL_USER_ID, ConsentTestFixtures.TENANT_A);
    }
    
    /**
     * Create a context for Dave (assistant) in Tenant A
     */
    public static SecurityContext daveContext() {
        return createContext(ConsentTestFixtures.DAVE_USER_ID, ConsentTestFixtures.TENANT_A);
    }
    
    /**
     * Create a context for Dave (assistant) acting on behalf of Carol
     */
    public static SecurityContext daveActingForCarolContext() {
        return createDelegatedContext(
                ConsentTestFixtures.DAVE_USER_ID,
                ConsentTestFixtures.CAROL_USER_ID,
                ConsentTestFixtures.TENANT_A
        );
    }
    
    /**
     * Create a context for Eve in Tenant B
     */
    public static SecurityContext eveContext() {
        return createContext(ConsentTestFixtures.EVE_USER_ID, ConsentTestFixtures.TENANT_B);
    }
    
    /**
     * Create a context with custom attributes
     */
    public static SecurityContext createContextWithAttributes(
            String userId, String tenantId, Map<String, Object> attributes) {
        return SecurityContext.builder()
                .userId(userId)
                .username(userId)
                .tenantId(tenantId)
                .roles(List.of("employee"))
                .isDelegated(false)
                .actorId(userId)
                .subjectId(userId)
                .attributes(attributes)
                .build();
    }
}