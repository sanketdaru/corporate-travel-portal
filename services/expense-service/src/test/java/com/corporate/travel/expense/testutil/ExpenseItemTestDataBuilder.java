package com.corporate.travel.expense.testutil;

import com.corporate.travel.expense.model.entity.Expense;
import com.corporate.travel.expense.model.entity.ExpenseItem;
import com.corporate.travel.models.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fluent builder for creating ExpenseItem test data
 * Follows the Test Data Builder pattern for maintainable test data creation
 */
public class ExpenseItemTestDataBuilder {
    
    private UUID id;
    private Expense expense;
    private LocalDate date = LocalDate.now().minusDays(3);
    private ExpenseCategory category = ExpenseCategory.MEALS;
    private String description = "Client dinner at restaurant";
    private BigDecimal amount = new BigDecimal("1500.00");
    private String currency = "INR";
    private String receiptUrl;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    private ExpenseItemTestDataBuilder() {
        // Private constructor to enforce factory method usage
    }
    
    /**
     * Factory method to create a new builder instance
     */
    public static ExpenseItemTestDataBuilder anExpenseItem() {
        return new ExpenseItemTestDataBuilder();
    }
    
    /**
     * Create an expense item with all default valid data
     */
    public static ExpenseItem aValidExpenseItem() {
        return anExpenseItem().build();
    }
    
    // ========== Field Setters ==========
    
    public ExpenseItemTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }
    
    public ExpenseItemTestDataBuilder withExpense(Expense expense) {
        this.expense = expense;
        return this;
    }
    
    public ExpenseItemTestDataBuilder withDate(LocalDate date) {
        this.date = date;
        return this;
    }
    
    public ExpenseItemTestDataBuilder withCategory(ExpenseCategory category) {
        this.category = category;
        return this;
    }
    
    public ExpenseItemTestDataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }
    
    public ExpenseItemTestDataBuilder withAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }
    
    public ExpenseItemTestDataBuilder withCurrency(String currency) {
        this.currency = currency;
        return this;
    }
    
    public ExpenseItemTestDataBuilder withReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
        return this;
    }
    
    public ExpenseItemTestDataBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }
    
    public ExpenseItemTestDataBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
    
    // ========== Category Helpers ==========
    
    public ExpenseItemTestDataBuilder asMealExpense() {
        this.category = ExpenseCategory.MEALS;
        this.description = "Client lunch at restaurant";
        this.amount = new BigDecimal("1500.00");
        return this;
    }
    
    public ExpenseItemTestDataBuilder asTravelExpense() {
        this.category = ExpenseCategory.TRAVEL;
        this.description = "Flight ticket to client site";
        this.amount = new BigDecimal("5000.00");
        return this;
    }
    
    public ExpenseItemTestDataBuilder asAccommodationExpense() {
        this.category = ExpenseCategory.ACCOMMODATION;
        this.description = "Hotel stay during business trip";
        this.amount = new BigDecimal("3500.00");
        return this;
    }
    
    public ExpenseItemTestDataBuilder asTransportationExpense() {
        this.category = ExpenseCategory.TRANSPORTATION;
        this.description = "Taxi to airport";
        this.amount = new BigDecimal("800.00");
        return this;
    }
    
    public ExpenseItemTestDataBuilder asOtherExpense() {
        this.category = ExpenseCategory.OTHER;
        this.description = "Miscellaneous business expense";
        this.amount = new BigDecimal("500.00");
        return this;
    }
    
    // ========== Amount Helpers ==========
    
    public ExpenseItemTestDataBuilder withSmallAmount() {
        this.amount = new BigDecimal("100.00");
        return this;
    }
    
    public ExpenseItemTestDataBuilder withMediumAmount() {
        this.amount = new BigDecimal("1500.00");
        return this;
    }
    
    public ExpenseItemTestDataBuilder withLargeAmount() {
        this.amount = new BigDecimal("10000.00");
        return this;
    }
    
    // ========== Date Helpers ==========
    
    public ExpenseItemTestDataBuilder withRecentDate() {
        this.date = LocalDate.now().minusDays(1);
        return this;
    }
    
    public ExpenseItemTestDataBuilder withOldDate() {
        this.date = LocalDate.now().minusDays(30);
        return this;
    }
    
    /**
     * Build the ExpenseItem entity
     */
    public ExpenseItem build() {
        return ExpenseItem.builder()
                .id(id)
                .expense(expense)
                .date(date)
                .category(category)
                .description(description)
                .amount(amount)
                .currency(currency)
                .receiptUrl(receiptUrl)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
