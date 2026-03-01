package com.corporate.travel.delegation.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Delegation Entity - PostgreSQL (Source of Truth)
 * 
 * Represents a delegation relationship where one user (delegator) grants
 * permission to another user (delegate) to act on their behalf.
 */
@Entity
@Table(name = "delegations", schema = "delegation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Delegation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @NotBlank
    @Column(name = "delegator_id", nullable = false)
    private String delegatorId;  // Carol - person granting delegation

    @NotBlank
    @Column(name = "delegate_id", nullable = false)
    private String delegateId;  // Dave - person receiving delegation

    @NotBlank
    @Column(name = "purpose", nullable = false, length = 500)
    private String purpose;  // "book_travel", "approve_expenses"

    @NotNull
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "scopes", nullable = false, columnDefinition = "text[]")
    private List<String> scopes;  // ["view_bookings", "create_bookings"]

    @NotNull
    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @NotBlank
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if delegation is currently valid
     */
    public boolean isValid() {
        if (!active) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Revoke this delegation
     */
    public void revoke(String revokedByUserId) {
        this.active = false;
        this.revokedAt = LocalDateTime.now();
        this.revokedBy = revokedByUserId;
        this.updatedBy = revokedByUserId;
        this.updatedAt = LocalDateTime.now();
    }
}