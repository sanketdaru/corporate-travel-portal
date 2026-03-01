package com.corporate.travel.delegation.testutil;

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
                .purpose("book_travel")
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
     * Create a context for Alice (employee) in Tenant A
     */
    public static SecurityContext aliceContext() {
        return createContext(DelegationTestFixtures.ALICE_USER_ID, DelegationTestFixtures.TENANT_A);
    }
    
    /**
     * Create a context for Bob (manager) in Tenant A
     */
    public static SecurityContext bobContext() {
        return createManagerContext(DelegationTestFixtures.BOB_USER_ID, DelegationTestFixtures.TENANT_A);
    }
    
    /**
     * Create a context for Carol (executive) in Tenant A
     */
    public static SecurityContext carolContext() {
        return createContext(DelegationTestFixtures.CAROL_USER_ID, DelegationTestFixtures.TENANT_A);
    }
    
    /**
     * Create a context for Dave (assistant) in Tenant A
     */
    public static SecurityContext daveContext() {
        return createContext(DelegationTestFixtures.DAVE_USER_ID, DelegationTestFixtures.TENANT_A);
    }
    
    /**
     * Create a context for Dave acting on behalf of Carol
     */
    public static SecurityContext daveActingForCarolContext() {
        return createDelegatedContext(
                DelegationTestFixtures.DAVE_USER_ID,
                DelegationTestFixtures.CAROL_USER_ID,
                DelegationTestFixtures.TENANT_A
        );
    }
    
    /**
     * Create a context for Eve in Tenant B
     */
    public static SecurityContext eveContext() {
        return createContext(DelegationTestFixtures.EVE_USER_ID, DelegationTestFixtures.TENANT_B);
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