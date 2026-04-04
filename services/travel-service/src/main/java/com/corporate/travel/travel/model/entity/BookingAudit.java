package com.corporate.travel.travel.model.entity;

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
 * Immutable audit record for booking actions (ADR-011).
 *
 * Captures the full delegation identity chain for every write:
 *   actor_id      — who performed the action (Dave in delegation scenarios)
 *   subject_id    — on whose behalf (Carol in delegation scenarios)
 *   delegation_id — links to the delegation record in delegation-service / Neo4j
 *   consent_id    — links to the consent record that authorised the action
 */
@Entity
@Table(name = "booking_audit", schema = "travel")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;  // CREATE, UPDATE, DELETE, STATUS_CHANGE

    @Column(name = "details", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "delegation_id")
    private UUID delegationId;

    @Column(name = "consent_id")
    private UUID consentId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
