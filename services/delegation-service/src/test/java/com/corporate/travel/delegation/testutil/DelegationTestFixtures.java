package com.corporate.travel.delegation.testutil;

import com.corporate.travel.delegation.model.entity.Delegation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Static factory methods for common delegation test scenarios
 * Provides pre-configured test fixtures for typical use cases
 */
public class DelegationTestFixtures {
    
    // Test user IDs (aligned with Travel Service)
    public static final String ALICE_USER_ID = "alice.employee";
    public static final String BOB_USER_ID = "bob.manager";
    public static final String CAROL_USER_ID = "carol.executive";
    public static final String DAVE_USER_ID = "dave.assistant";
    public static final String EVE_USER_ID = "eve.employee";
    
    // Test tenant IDs
    public static final String TENANT_A = "tenant-a";
    public static final String TENANT_B = "tenant-b";
    
    // Test delegation IDs
    public static final UUID DELEGATION_ID_1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID DELEGATION_ID_2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    public static final UUID DELEGATION_ID_3 = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    
    /**
     * Create an active delegation from Carol to Dave for booking travel
     */
    public static Delegation activeDelegationCarolToDave() {
        return DelegationTestDataBuilder.aDelegation()
                .withId(DELEGATION_ID_1)
                .withTenantId(TENANT_A)
                .withDelegatorId(CAROL_USER_ID)
                .withDelegateId(DAVE_USER_ID)
                .withPurpose("book_travel")
                .withScopes(List.of("view_bookings", "create_bookings"))
                .withActive(true)
                .withGrantedAt(LocalDateTime.now().minusDays(7))
                .build();
    }
    
    /**
     * Create an active delegation from Bob to Alice for expense management
     */
    public static Delegation activeDelegationBobToAlice() {
        return DelegationTestDataBuilder.aDelegation()
                .withId(DELEGATION_ID_2)
                .withTenantId(TENANT_A)
                .withDelegatorId(BOB_USER_ID)
                .withDelegateId(ALICE_USER_ID)
                .withPurpose("manage_expenses")
                .withScopes(List.of("view_expenses", "create_expenses", "submit_expenses"))
                .withActive(true)
                .build();
    }
    
    /**
     * Create an expired delegation
     */
    public static Delegation expiredDelegation() {
        return DelegationTestDataBuilder.aDelegation()
                .withId(DELEGATION_ID_3)
                .withTenantId(TENANT_A)
                .withDelegatorId(CAROL_USER_ID)
                .withDelegateId(DAVE_USER_ID)
                .withActive(true)
                .asExpired()
                .build();
    }
    
    /**
     * Create a revoked delegation
     */
    public static Delegation revokedDelegation() {
        Delegation delegation = DelegationTestDataBuilder.aDelegation()
                .withTenantId(TENANT_A)
                .withDelegatorId(CAROL_USER_ID)
                .withDelegateId(DAVE_USER_ID)
                .build();
        delegation.revoke(CAROL_USER_ID);
        return delegation;
    }
    
    /**
     * Create a delegation in Tenant B
     */
    public static Delegation delegationInTenantB() {
        return DelegationTestDataBuilder.aDelegation()
                .withTenantId(TENANT_B)
                .withDelegatorId(EVE_USER_ID)
                .withDelegateId("frank.employee")
                .withActive(true)
                .build();
    }
    
    /**
     * Create multiple delegations for testing list operations
     */
    public static List<Delegation> multipleDelegationsInTenantA() {
        List<Delegation> delegations = new ArrayList<>();
        delegations.add(activeDelegationCarolToDave());
        delegations.add(activeDelegationBobToAlice());
        return delegations;
    }
    
    /**
     * Create a delegation with future expiration
     */
    public static Delegation delegationWithFutureExpiration() {
        return DelegationTestDataBuilder.aDelegation()
                .withTenantId(TENANT_A)
                .withDelegatorId(CAROL_USER_ID)
                .withDelegateId(DAVE_USER_ID)
                .withFutureExpiration(30)
                .build();
    }
    
    /**
     * Create a delegation with minimal scopes
     */
    public static Delegation delegationWithSingleScope() {
        return DelegationTestDataBuilder.aDelegation()
                .withTenantId(TENANT_A)
                .withDelegatorId(CAROL_USER_ID)
                .withDelegateId(DAVE_USER_ID)
                .withScopes(List.of("view_bookings"))
                .build();
    }
    
    /**
     * Create a delegation with multiple scopes
     */
    public static Delegation delegationWithMultipleScopes() {
        return DelegationTestDataBuilder.aDelegation()
                .withTenantId(TENANT_A)
                .withDelegatorId(CAROL_USER_ID)
                .withDelegateId(DAVE_USER_ID)
                .withScopes(List.of(
                    "view_bookings",
                    "create_bookings",
                    "update_bookings",
                    "delete_bookings",
                    "view_expenses",
                    "create_expenses"
                ))
                .build();
    }
}
