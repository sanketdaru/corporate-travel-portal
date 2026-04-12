package com.corporate.travel.expense.model.entity;

import com.corporate.travel.models.ExpenseStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Expense entity representing an expense report
 * 
 * Contains multiple expense items and tracks approval workflow
 * Multi-tenant support via tenant_id column
 */
@Entity
@Table(name = "expenses", schema = "expense", indexes = {
    @Index(name = "idx_expenses_tenant", columnList = "tenant_id"),
    @Index(name = "idx_expenses_user", columnList = "user_id"),
    @Index(name = "idx_expenses_status", columnList = "status"),
    @Index(name = "idx_expenses_booking", columnList = "booking_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Tenant ID for multi-tenant isolation — populated server-side from JWT; not expected from client
     */
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    /**
     * User ID - owner of the expense report
     * In delegation scenarios, this is the subject — populated server-side from SecurityContext
     */
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;
    
    /**
     * Optional reference to a booking
     */
    @Column(name = "booking_id")
    private UUID bookingId;
    
    /**
     * Title/purpose of the expense report
     */
    @Column(name = "title", length = 500)
    private String title;
    
    /**
     * Description of the expense report
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * Total amount calculated from expense items
     */
    @Column(name = "total_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;
    
    /**
     * Currency code (ISO 4217)
     */
    @Column(name = "currency", length = 3, nullable = false)
    @NotNull
    @Builder.Default
    private String currency = "INR";
    
    /**
     * Current status of the expense report
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @NotNull
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.DRAFT;
    
    /**
     * Date when expense was submitted for approval
     */
    @Column(name = "submission_date")
    private LocalDateTime submissionDate;
    
    /**
     * Date when expense was approved/rejected
     */
    @Column(name = "approval_date")
    private LocalDateTime approvalDate;
    
    /**
     * ID of the manager who approved/rejected
     */
    @Column(name = "approver_id", length = 255)
    private String approverId;
    
    /**
     * Expense items (line items)
     */
    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ExpenseItem> items = new ArrayList<>();
    
    /**
     * Timestamp when expense was created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when expense was last updated
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * User who created the expense (actor in delegation scenarios)
     */
    @Column(name = "created_by", length = 255)
    private String createdBy;
    
    /**
     * User who last updated the expense
     */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
    
    /**
     * Helper method to add an expense item
     */
    public void addItem(ExpenseItem item) {
        items.add(item);
        item.setExpense(this);
        recalculateTotal();
    }
    
    /**
     * Helper method to remove an expense item
     */
    public void removeItem(ExpenseItem item) {
        items.remove(item);
        item.setExpense(null);
        recalculateTotal();
    }
    
    /**
     * Recalculate total amount from items
     */
    public void recalculateTotal() {
        this.totalAmount = items.stream()
            .map(ExpenseItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}