package com.corporate.travel.expense.testutil;

import com.corporate.travel.expense.model.entity.Expense;
import com.corporate.travel.expense.model.entity.ExpenseItem;
import com.corporate.travel.models.ExpenseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fluent builder for creating Expense test data
 * Follows the Test Data Builder pattern for maintainable test data creation
 */
public class ExpenseTestDataBuilder {
    
    private UUID id;
    private String tenantId = "tenant-a";
    private String userId = "alice.employee";
    private UUID bookingId;
    private String title = "Business Trip Expenses";
    private String description = "Expenses for client meeting";
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private String currency = "INR";
    private ExpenseStatus status = ExpenseStatus.DRAFT;
    private List<ExpenseItem> items = new ArrayList<>();
    private LocalDateTime submissionDate;
    private LocalDateTime approvalDate;
    private String approverId;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private String createdBy = "alice.employee";
    private String updatedBy = "alice.employee";
    
    private ExpenseTestDataBuilder() {
        // Private constructor to enforce factory method usage
    }
    
    /**
     * Factory method to create a new builder instance
     */
    public static ExpenseTestDataBuilder anExpense() {
        return new ExpenseTestDataBuilder();
    }
    
    /**
     * Create an expense with all default valid data
     */
    public static Expense aValidExpense() {
        return anExpense().build();
    }
    
    // ========== Field Setters ==========
    
    public ExpenseTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }
    
    public ExpenseTestDataBuilder withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }
    
    public ExpenseTestDataBuilder withUserId(String userId) {
        this.userId = userId;
        return this;
    }
    
    public ExpenseTestDataBuilder withBookingId(UUID bookingId) {
        this.bookingId = bookingId;
        return this;
    }
    
    public ExpenseTestDataBuilder withTitle(String title) {
        this.title = title;
        return this;
    }
    
    public ExpenseTestDataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }
    
    public ExpenseTestDataBuilder withTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }
    
    public ExpenseTestDataBuilder withCurrency(String currency) {
        this.currency = currency;
        return this;
    }
    
    public ExpenseTestDataBuilder withStatus(ExpenseStatus status) {
        this.status = status;
        return this;
    }
    
    public ExpenseTestDataBuilder withItems(List<ExpenseItem> items) {
        this.items = new ArrayList<>(items);
        return this;
    }
    
    public ExpenseTestDataBuilder withSubmissionDate(LocalDateTime submissionDate) {
        this.submissionDate = submissionDate;
        return this;
    }
    
    public ExpenseTestDataBuilder withApprovalDate(LocalDateTime approvalDate) {
        this.approvalDate = approvalDate;
        return this;
    }
    
    public ExpenseTestDataBuilder withApproverId(String approverId) {
        this.approverId = approverId;
        return this;
    }
    
    public ExpenseTestDataBuilder withCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
    
    public ExpenseTestDataBuilder withUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }
    
    public ExpenseTestDataBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }
    
    public ExpenseTestDataBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
    
    // ========== Status Helpers ==========
    
    public ExpenseTestDataBuilder inDraftStatus() {
        this.status = ExpenseStatus.DRAFT;
        this.submissionDate = null;
        this.approvalDate = null;
        this.approverId = null;
        return this;
    }
    
    public ExpenseTestDataBuilder inSubmittedStatus() {
        this.status = ExpenseStatus.SUBMITTED;
        this.submissionDate = LocalDateTime.now().minusDays(1);
        this.approvalDate = null;
        this.approverId = null;
        return this;
    }
    
    public ExpenseTestDataBuilder inApprovedStatus() {
        this.status = ExpenseStatus.APPROVED;
        this.submissionDate = LocalDateTime.now().minusDays(2);
        this.approvalDate = LocalDateTime.now().minusDays(1);
        this.approverId = "bob.manager";
        return this;
    }
    
    public ExpenseTestDataBuilder inRejectedStatus() {
        this.status = ExpenseStatus.REJECTED;
        this.submissionDate = LocalDateTime.now().minusDays(2);
        this.approvalDate = LocalDateTime.now().minusDays(1);
        this.approverId = "bob.manager";
        return this;
    }
    
    public ExpenseTestDataBuilder inPaidStatus() {
        this.status = ExpenseStatus.PAID;
        this.submissionDate = LocalDateTime.now().minusDays(5);
        this.approvalDate = LocalDateTime.now().minusDays(3);
        this.approverId = "bob.manager";
        return this;
    }
    
    // ========== Item Helpers ==========
    
    public ExpenseTestDataBuilder withNoItems() {
        this.items = new ArrayList<>();
        this.totalAmount = BigDecimal.ZERO;
        return this;
    }
    
    public ExpenseTestDataBuilder withSingleItem(ExpenseItem item) {
        this.items = new ArrayList<>();
        this.items.add(item);
        this.totalAmount = item.getAmount();
        return this;
    }
    
    public ExpenseTestDataBuilder withMultipleItems(int count) {
        this.items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ExpenseItem item = ExpenseItemTestDataBuilder.anExpenseItem()
                    .withAmount(new BigDecimal("500.00"))
                    .build();
            this.items.add(item);
        }
        this.totalAmount = new BigDecimal("500.00").multiply(new BigDecimal(count));
        return this;
    }
    
    public ExpenseTestDataBuilder withMealExpenseItems() {
        this.items = new ArrayList<>();
        this.items.add(ExpenseItemTestDataBuilder.anExpenseItem().asMealExpense().build());
        this.items.add(ExpenseItemTestDataBuilder.anExpenseItem().asMealExpense()
                .withAmount(new BigDecimal("800.00")).build());
        this.totalAmount = new BigDecimal("2300.00"); // 1500 + 800
        return this;
    }
    
    public ExpenseTestDataBuilder withTravelExpenseItems() {
        this.items = new ArrayList<>();
        this.items.add(ExpenseItemTestDataBuilder.anExpenseItem().asTravelExpense().build());
        this.totalAmount = new BigDecimal("5000.00");
        return this;
    }
    
    /**
     * Build the Expense entity
     */
    public Expense build() {
        Expense expense = Expense.builder()
                .id(id)
                .tenantId(tenantId)
                .userId(userId)
                .bookingId(bookingId)
                .title(title)
                .description(description)
                .totalAmount(totalAmount)
                .currency(currency)
                .status(status)
                .items(new ArrayList<>())
                .submissionDate(submissionDate)
                .approvalDate(approvalDate)
                .approverId(approverId)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .createdBy(createdBy)
                .updatedBy(updatedBy)
                .build();
        
        // Add items with proper bidirectional relationship
        for (ExpenseItem item : items) {
            expense.addItem(item);
        }
        
        return expense;
    }
}