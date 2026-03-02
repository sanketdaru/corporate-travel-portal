package com.corporate.travel.consent.testutil;

import com.corporate.travel.consent.model.entity.Consent;
import com.corporate.travel.consent.model.entity.ConsentStatus;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * Static factory methods for common consent test scenarios
 * Provides pre-configured test fixtures for typical use cases
 */
public class ConsentTestFixtures {
    
    // Test user IDs
    public static final String ALICE_USER_ID = "alice.employee";
    public static final String BOB_USER_ID = "bob.manager";
    public static final String CAROL_USER_ID = "carol.executive";
    public static final String DAVE_USER_ID = "dave.assistant";
    public static final String EVE_USER_ID = "eve.employee";
    
    // Test tenant IDs
    public static final String TENANT_A = "tenant-a";
    public static final String TENANT_B = "tenant-b";
    
    // Test consent IDs
    public static final UUID CONSENT_ID_1 = UUID.fromString("c1111111-1111-1111-1111-111111111111");
    public static final UUID CONSENT_ID_2 = UUID.fromString("c2222222-2222-2222-2222-222222222222");
    public static final UUID CONSENT_ID_3 = UUID.fromString("c3333333-3333-3333-3333-333333333333");
    public static final UUID CONSENT_ID_4 = UUID.fromString("c4444444-4444-4444-4444-444444444444");
    
    // Test delegation IDs
    public static final UUID DELEGATION_ID_1 = UUID.fromString("d1111111-1111-1111-1111-111111111111");
    
    // ========== Active Consent Fixtures ==========
    
    /**
     * Create an active consent from Alice to Dave for booking travel
     */
    public static Consent activeConsentAliceToDave() {
        return ConsentTestDataBuilder.aConsent()
                .withId(CONSENT_ID_1)
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .forBookingPurpose()
                .inActiveStatus()
                .withNoExpiry()
                .build();
    }
    
    /**
     * Create an active consent with multiple scopes
     */
    public static Consent activeConsentWithMultipleScopes() {
        return ConsentTestDataBuilder.aConsent()
                .withId(CONSENT_ID_1)
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .withPurpose("manage_travel")
                .withFullScopes()
                .inActiveStatus()
                .withNoExpiry()
                .build();
    }
    
    /**
     * Create an active consent with future expiry
     */
    public static Consent activeConsentWithFutureExpiry() {
        return ConsentTestDataBuilder.aConsent()
                .withId(CONSENT_ID_2)
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .forBookingPurpose()
                .inActiveStatus()
                .withFutureExpiry(30)
                .build();
    }
    
    /**
     * Create an active consent with no expiry date
     */
    public static Consent activeConsentNoExpiry() {
        return ConsentTestDataBuilder.aConsent()
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .forBookingPurpose()
                .inActiveStatus()
                .withNoExpiry()
                .build();
    }
    
    /**
     * Create an active consent from Carol to Bob
     */
    public static Consent activeConsentCarolToBob() {
        return ConsentTestDataBuilder.aConsent()
                .withId(CONSENT_ID_3)
                .withTenantId(TENANT_A)
                .fromCarolToBob()
                .forExpensePurpose()
                .inActiveStatus()
                .withNoExpiry()
                .build();
    }
    
    // ========== Expired Consent Fixtures ==========
    
    /**
     * Create an expired consent from Alice to Dave
     */
    public static Consent expiredConsentAliceToDave() {
        return ConsentTestDataBuilder.aConsent()
                .withId(CONSENT_ID_2)
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .forBookingPurpose()
                .inExpiredStatus()
                .withPastExpiry()
                .build();
    }
    
    /**
     * Create a consent that expires soon (5 minutes)
     */
    public static Consent consentExpiringSoon() {
        return ConsentTestDataBuilder.aConsent()
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .forBookingPurpose()
                .inActiveStatus()
                .withExpiryInMinutes(5)
                .build();
    }
    
    // ========== Revoked Consent Fixtures ==========
    
    /**
     * Create a revoked consent from Carol to Bob
     */
    public static Consent revokedConsentCarolToBob() {
        return ConsentTestDataBuilder.aConsent()
                .withId(CONSENT_ID_3)
                .withTenantId(TENANT_A)
                .fromCarolToBob()
                .forExpensePurpose()
                .inRevokedStatus()
                .build();
    }
    
    /**
     * Create a revoked consent from Alice to Dave
     */
    public static Consent revokedConsentAliceToDave() {
        return ConsentTestDataBuilder.aConsent()
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .forBookingPurpose()
                .inRevokedStatus()
                .build();
    }
    
    // ========== Delegation Consent Fixtures ==========
    
    /**
     * Create a delegated consent where Dave acts for Carol
     */
    public static Consent delegatedConsentDaveForCarol() {
        return ConsentTestDataBuilder.aConsent()
                .withId(CONSENT_ID_4)
                .withTenantId(TENANT_A)
                .withGrantorId(CAROL_USER_ID)
                .withGranteeId(DAVE_USER_ID)
                .withDelegationId(DELEGATION_ID_1)
                .withPurpose("act_on_behalf")
                .withFullScopes()
                .inActiveStatus()
                .withCreatedBy(DAVE_USER_ID)
                .withUpdatedBy(DAVE_USER_ID)
                .withNoExpiry()
                .build();
    }
    
    // ========== Tenant Isolation Fixtures ==========
    
    /**
     * Create a consent in Tenant B (for Eve)
     */
    public static Consent consentInTenantB() {
        return ConsentTestDataBuilder.aConsent()
                .withTenantId(TENANT_B)
                .withGrantorId(EVE_USER_ID)
                .withGranteeId("frank.colleague")
                .withCreatedBy(EVE_USER_ID)
                .withUpdatedBy(EVE_USER_ID)
                .forBookingPurpose()
                .inActiveStatus()
                .withNoExpiry()
                .build();
    }
    
    // ========== Duplicate & Validation Fixtures ==========
    
    /**
     * Create a duplicate active consent (for duplicate testing)
     */
    public static Consent duplicateActiveConsent() {
        return ConsentTestDataBuilder.aConsent()
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .withPurpose("book_travel")  // Same purpose
                .withScopes(Arrays.asList("view_bookings", "create_bookings"))
                .inActiveStatus()
                .withNoExpiry()
                .build();
    }
    
    /**
     * Create a consent ready for validation (active with specific scopes)
     */
    public static Consent consentReadyForValidation() {
        return ConsentTestDataBuilder.aConsent()
                .withId(CONSENT_ID_1)
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .withPurpose("book_travel")
                .withScopes(Arrays.asList("view_bookings", "create_bookings", "update_bookings"))
                .inActiveStatus()
                .withNoExpiry()
                .build();
    }
    
    /**
     * Create a consent with partial scopes (for partial match testing)
     */
    public static Consent consentWithPartialScopes() {
        return ConsentTestDataBuilder.aConsent()
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .withPurpose("book_travel")
                .withScopes(Arrays.asList("view_bookings"))  // Only one scope
                .inActiveStatus()
                .withNoExpiry()
                .build();
    }
    
    /**
     * Create a consent with single scope
     */
    public static Consent consentWithSingleScope() {
        return ConsentTestDataBuilder.aConsent()
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .withPurpose("view_only")
                .withSingleScope("view_bookings")
                .inActiveStatus()
                .withNoExpiry()
                .build();
    }
    
    /**
     * Create a consent with metadata
     */
    public static Consent consentWithMetadata() {
        return ConsentTestDataBuilder.aConsent()
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .forBookingPurpose()
                .inActiveStatus()
                .withReasonMetadata("Business trip assistance")
                .withMetadataEntry("department", "Sales")
                .withNoExpiry()
                .build();
    }
    
    /**
     * Create a consent that's about to expire
     */
    public static Consent consentExpiringToday() {
        return ConsentTestDataBuilder.aConsent()
                .withTenantId(TENANT_A)
                .fromAliceToDave()
                .forBookingPurpose()
                .inActiveStatus()
                .withExpiresAt(LocalDateTime.now().plusHours(2))
                .build();
    }
}
