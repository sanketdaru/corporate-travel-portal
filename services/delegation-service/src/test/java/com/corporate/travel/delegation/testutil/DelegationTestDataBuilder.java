package com.corporate.travel.delegation.testutil;

import com.corporate.travel.delegation.model.entity.Delegation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fluent builder for creating Delegation test data
 * Follows the Test Data Builder pattern for maintainable test data creation
 */
public class DelegationTestDataBuilder {
    
    private UUID id;
    private String tenantId = "tenant-a";
    private String delegatorId = "carol.executive";
    private String delegateId = "dave.assistant";
    private String purpose = "book_travel";
    private List<String> scopes = new ArrayList<>(List.of("view_bookings", "create_bookings"));
    private LocalDateTime grantedAt = LocalDateTime.now();
    private LocalDateTime expiresAt = null;
    private Boolean active = true;
    private LocalDateTime revokedAt = null;
    private String revokedBy = null;
    private String createdBy = "carol.executive";
    private LocalDateTime createdAt = LocalDateTime.now();
    private String updatedBy = null;
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    private DelegationTestDataBuilder() {
        // Private constructor to enforce factory method usage
    }
    
    /**
     * Factory method to create a new builder instance
     */
    public static DelegationTestDataBuilder aDelegation() {
        return new DelegationTestDataBuilder();
    }
    
    /**
     * Create a delegation with all default valid data
     */
    public static Delegation aValidDelegation() {
        return aDelegation().build();
    }
    
    public DelegationTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }
    
    public DelegationTestDataBuilder withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }
    
    public DelegationTestDataBuilder withDelegatorId(String delegatorId) {
        this.delegatorId = delegatorId;
        this.createdBy = delegatorId; // Typically delegator creates it
        return this;
    }
    
    public DelegationTestDataBuilder withDelegateId(String delegateId) {
        this.delegateId = delegateId;
        return this;
    }
    
    public DelegationTestDataBuilder withPurpose(String purpose) {
        this.purpose = purpose;
        return this;
    }
    
    public DelegationTestDataBuilder withScopes(List<String> scopes) {
        this.scopes = new ArrayList<>(scopes);
        return this;
    }
    
    public DelegationTestDataBuilder withGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
        return this;
    }
    
    public DelegationTestDataBuilder withExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }
    
    public DelegationTestDataBuilder withActive(Boolean active) {
        this.active = active;
        return this;
    }
    
    public DelegationTestDataBuilder withRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
        return this;
    }
    
    public DelegationTestDataBuilder withRevokedBy(String revokedBy) {
        this.revokedBy = revokedBy;
        return this;
    }
    
    public DelegationTestDataBuilder withCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
    
    public DelegationTestDataBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }
    
    public DelegationTestDataBuilder withUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }
    
    public DelegationTestDataBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
    
    /**
     * Configure as expired delegation
     */
    public DelegationTestDataBuilder asExpired() {
        this.expiresAt = LocalDateTime.now().minusDays(1);
        return this;
    }
    
    /**
     * Configure as future expiration
     */
    public DelegationTestDataBuilder withFutureExpiration(int days) {
        this.expiresAt = LocalDateTime.now().plusDays(days);
        return this;
    }
    
    /**
     * Configure as revoked delegation
     */
    public DelegationTestDataBuilder asRevoked(String revokedBy) {
        this.active = false;
        this.revokedAt = LocalDateTime.now();
        this.revokedBy = revokedBy;
        return this;
    }
    
    /**
     * Configure with travel booking scopes
     */
    public DelegationTestDataBuilder withBookingScopes() {
        this.purpose = "book_travel";
        this.scopes = new ArrayList<>(List.of("view_bookings", "create_bookings", "update_bookings"));
        return this;
    }
    
    /**
     * Configure with expense management scopes
     */
    public DelegationTestDataBuilder withExpenseScopes() {
        this.purpose = "manage_expenses";
        this.scopes = new ArrayList<>(List.of("view_expenses", "create_expenses", "submit_expenses"));
        return this;
    }
    
    /**
     * Configure with approval scopes
     */
    public DelegationTestDataBuilder withApprovalScopes() {
        this.purpose = "approve_requests";
        this.scopes = new ArrayList<>(List.of("view_approvals", "approve_expenses"));
        return this;
    }
    
    /**
     * Build the Delegation entity
     */
    public Delegation build() {
        return Delegation.builder()
                .id(id)
                .tenantId(tenantId)
                .delegatorId(delegatorId)
                .delegateId(delegateId)
                .purpose(purpose)
                .scopes(scopes)
                .grantedAt(grantedAt)
                .expiresAt(expiresAt)
                .active(active)
                .revokedAt(revokedAt)
                .revokedBy(revokedBy)
                .createdBy(createdBy)
                .createdAt(createdAt)
                .updatedBy(updatedBy)
                .updatedAt(updatedAt)
                .build();
    }
}