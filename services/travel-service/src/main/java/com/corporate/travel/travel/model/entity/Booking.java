package com.corporate.travel.travel.model.entity;

import com.corporate.travel.models.BookingStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Booking entity representing a travel booking (flight, hotel, car rental)
 * 
 * Multi-tenant support via tenant_id column
 * Ownership tracked via user_id column
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
     * Tenant ID for multi-tenant isolation
     * Every query must filter by this field
     */
    @Column(name = "tenant_id", nullable = false, length = 255)
    @NotNull
    private String tenantId;
    
    /**
     * User ID - owner of the booking
     * In delegation scenarios, this is the subject (person being acted on behalf of)
     */
    @Column(name = "user_id", nullable = false, length = 255)
    @NotNull
    private String userId;
    
    /**
     * Type of booking: FLIGHT, HOTEL, CAR
     */
    @Column(name = "booking_type", nullable = false, length = 50)
    @NotNull
    private String bookingType;
    
    /**
     * Destination city or location
     */
    @Column(name = "destination", length = 255)
    private String destination;
    
    /**
     * Start date of travel
     */
    @Column(name = "start_date")
    private LocalDate startDate;
    
    /**
     * End date of travel
     */
    @Column(name = "end_date")
    private LocalDate endDate;
    
    /**
     * Current status of the booking
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @NotNull
    private BookingStatus status;
    
    /**
     * Total amount for the booking
     */
    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    /**
     * Additional details as JSON
     * e.g., flight numbers, hotel name, confirmation codes
     */
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;
    
    /**
     * Timestamp when booking was created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when booking was last updated
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * User who created the booking (actor in delegation scenarios)
     * For now, same as user_id unless delegated
     */
    @Column(name = "created_by", length = 255)
    private String createdBy;
    
    /**
     * User who last updated the booking
     */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
