package com.corporate.travel.consent.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Consent audit entity - Tracks all consent lifecycle events
 */
@Entity
@Table(name = "consent_audit", schema = "consent")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "consent_id", nullable = false)
    private UUID consentId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;  // GRANTED, USED, REVOKED, EXPIRED

    @Column(name = "actor_id", nullable = false)
    private String actorId;  // Who performed the action

    @Column(name = "subject_id")
    private String subjectId;  // On whose behalf (for delegation)

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "details", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}