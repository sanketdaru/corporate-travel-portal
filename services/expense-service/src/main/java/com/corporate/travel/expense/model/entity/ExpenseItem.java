package com.corporate.travel.expense.model.entity;

import com.corporate.travel.models.ExpenseCategory;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * ExpenseItem entity representing a line item in an expense report
 */
@Entity
@Table(name = "expense_items", schema = "expense", indexes = {
    @Index(name = "idx_expense_items_expense", columnList = "expense_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Reference to parent expense report
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    @JsonIgnore
    private Expense expense;
    
    /**
     * Date of the expense
     */
    @Column(name = "date", nullable = false)
    @NotNull
    private LocalDate date;
    
    /**
     * Category of the expense
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 100)
    @NotNull
    private ExpenseCategory category;
    
    /**
     * Description of the expense item
     */
    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    @NotNull
    private String description;
    
    /**
     * Amount of the expense
     */
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    @NotNull
    private BigDecimal amount;
    
    /**
     * Currency code (ISO 4217)
     */
    @Column(name = "currency", length = 3, nullable = false)
    @NotNull
    @Builder.Default
    private String currency = "INR";
    
    /**
     * URL to receipt image/document
     */
    @Column(name = "receipt_url", columnDefinition = "TEXT")
    private String receiptUrl;
    
    /**
     * Timestamp when item was created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when item was last updated
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}