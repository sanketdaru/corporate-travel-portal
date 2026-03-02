package com.corporate.travel.consent.testutil;

import com.corporate.travel.consent.model.entity.Consent;
import com.corporate.travel.consent.model.entity.ConsentAudit;
import com.corporate.travel.consent.model.entity.ConsentStatus;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Fluent builder for creating Consent test data
 * Follows the Test Data Builder pattern for maintainable test data creation
 */
public class ConsentTestDataBuilder {
    
    private UUID id;
    private String tenantId = "tenant-a";
    private String grantorId = "alice.employee";
    private String granteeId = "dave.assistant";
    private UUID delegationId;
    private String purpose = "book_travel";
    private List<String> scopes = Arrays.asList("view_bookings", "create_bookings");
    private List<String> dataCategories = Arrays.asList("travel_data");
    private LocalDateTime grantedAt = LocalDateTime.now().minusDays(1);
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String revokedBy;
    private ConsentStatus status = ConsentStatus.ACTIVE;
    private Map<String, Object> metadata = new HashMap<>();
    private String createdBy = "alice.employee";
    private LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
    private String updatedBy = "alice.employee";
    private LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
    
    private ConsentTestDataBuilder() {
        // Private constructor to enforce factory method usage
    }
    
    /**
     * Factory method to create a new builder instance
     */
    public static ConsentTestDataBuilder aConsent() {
        return new ConsentTestDataBuilder();
    }
    
    /**
     * Alias for aConsent() to match test code patterns
     */
    public static ConsentTestDataBuilder builder() {
        return aConsent();
    }
    
    /**
     * Create a consent with all default valid data
     */
    public static Consent aValidConsent() {
        return aConsent().build();
    }
    
    // ========== Field Setters ==========
    
    public ConsentTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }
    
    public ConsentTestDataBuilder withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }
    
    public ConsentTestDataBuilder withGrantorId(String grantorId) {
        this.grantorId = grantorId;
        return this;
    }
    
    public ConsentTestDataBuilder withGranteeId(String granteeId) {
        this.granteeId = granteeId;
        return this;
    }
    
    public ConsentTestDataBuilder withDelegationId(UUID delegationId) {
        this.delegationId = delegationId;
        return this;
    }
    
    public ConsentTestDataBuilder withPurpose(String purpose) {
        this.purpose = purpose;
        return this;
    }
    
    public ConsentTestDataBuilder withScopes(List<String> scopes) {
        this.scopes = new ArrayList<>(scopes);
        return this;
    }
    
    public ConsentTestDataBuilder withDataCategories(List<String> dataCategories) {
        this.dataCategories = new ArrayList<>(dataCategories);
        return this;
    }
    
    public ConsentTestDataBuilder withGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
        return this;
    }
    
    public ConsentTestDataBuilder withExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }
    
    public ConsentTestDataBuilder withRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
        return this;
    }
    
    public ConsentTestDataBuilder withRevokedBy(String revokedBy) {
        this.revokedBy = revokedBy;
        return this;
    }
    
    public ConsentTestDataBuilder withStatus(ConsentStatus status) {
        this.status = status;
        return this;
    }
    
    public ConsentTestDataBuilder withMetadata(Map<String, Object> metadata) {
        this.metadata = new HashMap<>(metadata);
        return this;
    }
    
    public ConsentTestDataBuilder withCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
    
    public ConsentTestDataBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }
    
    public ConsentTestDataBuilder withUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }
    
    public ConsentTestDataBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
    
    // ========== Status Helpers ==========
    
    public ConsentTestDataBuilder inActiveStatus() {
        this.status = ConsentStatus.ACTIVE;
        this.revokedAt = null;
        this.revokedBy = null;
        return this;
    }
    
    public ConsentTestDataBuilder inRevokedStatus() {
        this.status = ConsentStatus.REVOKED;
        this.revokedAt = LocalDateTime.now();
        this.revokedBy = this.grantorId;
        return this;
    }
    
    public ConsentTestDataBuilder inExpiredStatus() {
        this.status = ConsentStatus.EXPIRED;
        this.expiresAt = LocalDateTime.now().minusDays(1);
        return this;
    }
    
    // ========== Scope Helpers ==========
    
    public ConsentTestDataBuilder withSingleScope(String scope) {
        this.scopes = Arrays.asList(scope);
        return this;
    }
    
    public ConsentTestDataBuilder withMultipleScopes(String... scopes) {
        this.scopes = Arrays.asList(scopes);
        return this;
    }
    
    public ConsentTestDataBuilder withNoScopes() {
        this.scopes = new ArrayList<>();
        return this;
    }
    
    public ConsentTestDataBuilder withViewScopes() {
        this.scopes = Arrays.asList("view_bookings", "view_expenses");
        return this;
    }
    
    public ConsentTestDataBuilder withFullScopes() {
        this.scopes = Arrays.asList("view_bookings", "create_bookings", 
                                    "update_bookings", "delete_bookings");
        return this;
    }
    
    // ========== Expiry Helpers ==========
    
    public ConsentTestDataBuilder withFutureExpiry() {
        this.expiresAt = LocalDateTime.now().plusDays(30);
        return this;
    }
    
    public ConsentTestDataBuilder withFutureExpiry(int days) {
        this.expiresAt = LocalDateTime.now().plusDays(days);
        return this;
    }
    
    public ConsentTestDataBuilder withPastExpiry() {
        this.expiresAt = LocalDateTime.now().minusDays(1);
        this.status = ConsentStatus.EXPIRED;
        return this;
    }
    
    public ConsentTestDataBuilder withNoExpiry() {
        this.expiresAt = null;
        return this;
    }
    
    public ConsentTestDataBuilder withExpiryInMinutes(int minutes) {
        this.expiresAt = LocalDateTime.now().plusMinutes(minutes);
        return this;
    }
    
    // ========== Purpose Helpers ==========
    
    public ConsentTestDataBuilder forBookingPurpose() {
        this.purpose = "book_travel";
        this.scopes = Arrays.asList("view_bookings", "create_bookings");
        this.dataCategories = Arrays.asList("travel_data");
        return this;
    }
    
    public ConsentTestDataBuilder forExpensePurpose() {
        this.purpose = "manage_expenses";
        this.scopes = Arrays.asList("view_expenses", "create_expenses", "submit_expenses");
        this.dataCategories = Arrays.asList("expense_data");
        return this;
    }
    
    public ConsentTestDataBuilder forApprovalPurpose() {
        this.purpose = "approve_requests";
        this.scopes = Arrays.asList("view_approvals", "approve", "reject");
        this.dataCategories = Arrays.asList("approval_data");
        return this;
    }
    
    // ========== User Helpers ==========
    
    public ConsentTestDataBuilder fromAliceToDave() {
        this.grantorId = "alice.employee";
        this.granteeId = "dave.assistant";
        this.createdBy = "alice.employee";
        this.updatedBy = "alice.employee";
        return this;
    }
    
    public ConsentTestDataBuilder fromCarolToBob() {
        this.grantorId = "carol.executive";
        this.granteeId = "bob.manager";
        this.createdBy = "carol.executive";
        this.updatedBy = "carol.executive";
        return this;
    }
    
    public ConsentTestDataBuilder fromAliceToBob() {
        this.grantorId = "alice.employee";
        this.granteeId = "bob.manager";
        this.createdBy = "alice.employee";
        this.updatedBy = "alice.employee";
        return this;
    }
    
    // ========== Metadata Helpers ==========
    
    public ConsentTestDataBuilder withMetadataEntry(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
    
    public ConsentTestDataBuilder withReasonMetadata(String reason) {
        this.metadata.put("reason", reason);
        return this;
    }
    
    /**
     * Build the Consent entity
     */
    public Consent build() {
        return Consent.builder()
                .id(id)
                .tenantId(tenantId)
                .grantorId(grantorId)
                .granteeId(granteeId)
                .delegationId(delegationId)
                .purpose(purpose)
                .scopes(scopes)
                .dataCategories(dataCategories)
                .grantedAt(grantedAt)
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .revokedBy(revokedBy)
                .status(status)
                .metadata(metadata)
                .createdBy(createdBy)
                .createdAt(createdAt)
                .updatedBy(updatedBy)
                .updatedAt(updatedAt)
                .build();
    }
    
    // ==========================================================================
    // CONSENT AUDIT BUILDER
    // ==========================================================================
    
    /**
     * Factory method to create a new ConsentAuditBuilder instance
     */
    public static ConsentAuditBuilder auditBuilder() {
        return new ConsentAuditBuilder();
    }
    
    /**
     * Fluent builder for creating ConsentAudit test data
     */
    public static class ConsentAuditBuilder {
        private UUID id;
        private UUID consentId;
        private String action = "GRANTED";
        private String actorId = "alice.employee";
        private String subjectId;
        private LocalDateTime timestamp = LocalDateTime.now();
        private Map<String, Object> details = new HashMap<>();
        private String tenantId = "tenant-a";
        
        private ConsentAuditBuilder() {
            // Private constructor to enforce factory method usage
        }
        
        public ConsentAuditBuilder id(UUID id) {
            this.id = id;
            return this;
        }
        
        public ConsentAuditBuilder consentId(UUID consentId) {
            this.consentId = consentId;
            return this;
        }
        
        public ConsentAuditBuilder action(String action) {
            this.action = action;
            return this;
        }
        
        public ConsentAuditBuilder actorId(String actorId) {
            this.actorId = actorId;
            return this;
        }
        
        public ConsentAuditBuilder subjectId(String subjectId) {
            this.subjectId = subjectId;
            return this;
        }
        
        public ConsentAuditBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public ConsentAuditBuilder details(Map<String, Object> details) {
            this.details = new HashMap<>(details);
            return this;
        }
        
        public ConsentAuditBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        // ========== Action Helpers ==========
        
        public ConsentAuditBuilder granted() {
            this.action = "GRANTED";
            return this;
        }
        
        public ConsentAuditBuilder used() {
            this.action = "USED";
            return this;
        }
        
        public ConsentAuditBuilder revoked() {
            this.action = "REVOKED";
            return this;
        }
        
        public ConsentAuditBuilder expired() {
            this.action = "EXPIRED";
            return this;
        }
        
        // ========== User Helpers ==========
        
        public ConsentAuditBuilder byAlice() {
            this.actorId = "alice.employee";
            this.tenantId = "tenant-a";
            return this;
        }
        
        public ConsentAuditBuilder byDave() {
            this.actorId = "dave.assistant";
            this.tenantId = "tenant-a";
            return this;
        }
        
        /**
         * Build the ConsentAudit entity
         */
        public ConsentAudit build() {
            return ConsentAudit.builder()
                    .id(id)
                    .consentId(consentId)
                    .action(action)
                    .actorId(actorId)
                    .subjectId(subjectId)
                    .timestamp(timestamp)
                    .details(details)
                    .tenantId(tenantId)
                    .build();
        }
    }
}
