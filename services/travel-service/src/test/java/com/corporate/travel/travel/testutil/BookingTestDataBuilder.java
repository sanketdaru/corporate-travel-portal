package com.corporate.travel.travel.testutil;

import com.corporate.travel.models.BookingStatus;
import com.corporate.travel.travel.model.entity.Booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fluent builder for creating Booking test data.
 * Follows the Test Data Builder pattern for maintainable test data creation.
 *
 * A Booking is a travel authorization, not a completed transaction.
 * It carries a pre-approved budget; actual costs live on ExpenseItems.
 */
public class BookingTestDataBuilder {

    private UUID id;
    private String tenantId = "tenant-a";
    private String userId = "user-123";
    private String destination = "New York, USA";
    private LocalDate startDate = LocalDate.now().plusDays(7);
    private LocalDate endDate = LocalDate.now().plusDays(14);
    private String businessPurpose = "Annual sales conference";
    private String notes;
    private BookingStatus status = BookingStatus.PENDING;
    private BigDecimal budget = new BigDecimal("150000.00");
    private String budgetCurrency = "INR";
    private String details;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private String createdBy = "user-123";
    private String updatedBy = "user-123";

    private BookingTestDataBuilder() {}

    public static BookingTestDataBuilder aBooking() {
        return new BookingTestDataBuilder();
    }

    public static Booking aValidBooking() {
        return aBooking().build();
    }

    public BookingTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public BookingTestDataBuilder withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public BookingTestDataBuilder withUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public BookingTestDataBuilder withDestination(String destination) {
        this.destination = destination;
        return this;
    }

    public BookingTestDataBuilder withStartDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public BookingTestDataBuilder withEndDate(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public BookingTestDataBuilder withBusinessPurpose(String businessPurpose) {
        this.businessPurpose = businessPurpose;
        return this;
    }

    public BookingTestDataBuilder withNotes(String notes) {
        this.notes = notes;
        return this;
    }

    public BookingTestDataBuilder withStatus(BookingStatus status) {
        this.status = status;
        return this;
    }

    public BookingTestDataBuilder withBudget(BigDecimal budget) {
        this.budget = budget;
        return this;
    }

    public BookingTestDataBuilder withBudgetCurrency(String budgetCurrency) {
        this.budgetCurrency = budgetCurrency;
        return this;
    }

    public BookingTestDataBuilder withDetails(String details) {
        this.details = details;
        return this;
    }

    public BookingTestDataBuilder withCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public BookingTestDataBuilder withUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }

    public BookingTestDataBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public BookingTestDataBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    /** Configure for past travel dates (completed trip). */
    public BookingTestDataBuilder withPastDates() {
        this.startDate = LocalDate.now().minusDays(14);
        this.endDate = LocalDate.now().minusDays(7);
        return this;
    }

    /** Configure for future travel dates. */
    public BookingTestDataBuilder withFutureDates() {
        this.startDate = LocalDate.now().plusDays(30);
        this.endDate = LocalDate.now().plusDays(37);
        return this;
    }

    /** Configure for current/ongoing travel. */
    public BookingTestDataBuilder withCurrentDates() {
        this.startDate = LocalDate.now().minusDays(2);
        this.endDate = LocalDate.now().plusDays(5);
        return this;
    }

    public Booking build() {
        return Booking.builder()
                .id(id)
                .tenantId(tenantId)
                .userId(userId)
                .destination(destination)
                .startDate(startDate)
                .endDate(endDate)
                .businessPurpose(businessPurpose)
                .notes(notes)
                .status(status)
                .budget(budget)
                .budgetCurrency(budgetCurrency)
                .details(details)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .createdBy(createdBy)
                .updatedBy(updatedBy)
                .build();
    }
}
