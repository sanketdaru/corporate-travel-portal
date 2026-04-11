package com.corporate.travel.travel.model.entity;

import com.corporate.travel.models.BookingStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Booking entity representing a travel authorization.
 *
 * A booking is a request to undertake a business trip with a pre-approved budget.
 * It is NOT a completed transaction — the actual costs (flights, hotel stays,
 * meals, transport) are captured later as expense line items when the employee
 * submits an expense report.
 *
 * Multi-tenant support via tenant_id column.
 * Ownership tracked via user_id column.
 */
@Entity
@Table(name = "bookings", schema = "travel", indexes = {
    @Index(name = "idx_bookings_tenant", columnList = "tenant_id"),
    @Index(name = "idx_bookings_user", columnList = "user_id"),
    @Index(name = "idx_bookings_tenant_user", columnList = "tenant_id,user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Tenant ID for multi-tenant isolation.
     * Every query must filter by this field.
     */
    @Column(name = "tenant_id", nullable = false, length = 255)
    @NotNull
    private String tenantId;

    /**
     * User ID — owner of the travel authorization.
     * In delegation scenarios this is the subject (person being acted on behalf of).
     */
    @Column(name = "user_id", nullable = false, length = 255)
    @NotNull
    private String userId;

    /**
     * Destination city / country for the trip.
     */
    @Column(name = "destination", nullable = false, length = 255)
    @NotNull
    @Size(min = 1, max = 255)
    private String destination;

    /**
     * First day of travel.
     */
    @Column(name = "start_date", nullable = false)
    @NotNull
    private LocalDate startDate;

    /**
     * Last day of travel (must be on or after start_date).
     */
    @Column(name = "end_date", nullable = false)
    @NotNull
    private LocalDate endDate;

    /**
     * Business reason for the trip.
     */
    @Column(name = "business_purpose", columnDefinition = "TEXT")
    private String businessPurpose;

    /**
     * Optional freeform notes (visa requirements, special arrangements, etc.)
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Current status of the travel authorization.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @NotNull
    private BookingStatus status;

    /**
     * Pre-approved spending ceiling for the trip.
     * Expense submission total must not exceed this value.
     */
    @Column(name = "budget", precision = 12, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.01", message = "Budget must be greater than zero")
    private BigDecimal budget;

    /**
     * ISO 4217 currency code for the budget (e.g. INR, USD, EUR).
     */
    @Column(name = "budget_currency", nullable = false, length = 3)
    @NotNull
    @Builder.Default
    private String budgetCurrency = "INR";

    /**
     * Additional details as JSON (confirmation codes, notes, etc.)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    /**
     * Timestamp when the authorization was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the authorization was last updated.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * User who created the authorization (actor in delegation scenarios).
     */
    @Column(name = "created_by", length = 255)
    private String createdBy;

    /**
     * User who last updated the authorization.
     */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
