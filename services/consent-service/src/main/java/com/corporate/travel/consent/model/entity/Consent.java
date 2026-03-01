package com.corporate.travel.consent.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consent entity - Stores consent records with purpose binding
 */
@Entity
@Table(name = "consents", schema = "consent")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "grantor_id", nullable = false)
    private String grantorId;  // Person giving consent

    @Column(name = "grantee_id", nullable = false)
    private String granteeId;  // Person receiving consent

    @Column(name = "delegation_id")
    private UUID delegationId;  // Link to delegation

    @Column(name = "purpose", nullable = false, length = 500)
    private String purpose;

    @Column(name = "scopes", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> scopes;

    @Column(name = "data_categories")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> dataCategories;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ConsentStatus status;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ConsentStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if consent is currently valid (active and not expired)
     */
    public boolean isValid() {
        if (status != ConsentStatus.ACTIVE) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Check if consent includes a specific scope
     */
    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }

    /**
     * Check if consent includes all specified scopes
     */
    public boolean hasAllScopes(List<String> requiredScopes) {
        return scopes != null && scopes.containsAll(requiredScopes);
    }
}