package com.corporate.travel.travel.testutil;

import com.corporate.travel.models.BookingStatus;
import com.corporate.travel.travel.model.entity.Booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fluent builder for creating Booking test data
 * Follows the Test Data Builder pattern for maintainable test data creation
 */
public class BookingTestDataBuilder {
    
    private UUID id;
    private String tenantId = "tenant-a";
    private String userId = "user-123";
    private String bookingType = "FLIGHT";
    private String destination = "New York";
    private LocalDate startDate = LocalDate.now().plusDays(7);
    private LocalDate endDate = LocalDate.now().plusDays(14);
    private BookingStatus status = BookingStatus.PENDING;
    private BigDecimal totalAmount = new BigDecimal("1500.00");
    private String details = "{\"airline\":\"UA\",\"flightNumber\":\"UA123\"}";
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private String createdBy = "user-123";
    private String updatedBy = "user-123";
    
    private BookingTestDataBuilder() {
        // Private constructor to enforce factory method usage
    }
    
    /**
     * Factory method to create a new builder instance
     */
    public static BookingTestDataBuilder aBooking() {
        return new BookingTestDataBuilder();
    }
    
    /**
     * Create a booking with all default valid data
     */
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
    
    public BookingTestDataBuilder withBookingType(String bookingType) {
        this.bookingType = bookingType;
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
    
    public BookingTestDataBuilder withStatus(BookingStatus status) {
        this.status = status;
        return this;
    }
    
    public BookingTestDataBuilder withTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
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
    
    /**
     * Configure as a flight booking with default flight details
     */
    public BookingTestDataBuilder asFlightBooking() {
        this.bookingType = "FLIGHT";
        this.details = "{\"airline\":\"UA\",\"flightNumber\":\"UA123\",\"class\":\"economy\"}";
        return this;
    }
    
    /**
     * Configure as a hotel booking with default hotel details
     */
    public BookingTestDataBuilder asHotelBooking() {
        this.bookingType = "HOTEL";
        this.details = "{\"hotelName\":\"Hilton\",\"roomType\":\"standard\",\"confirmationCode\":\"HLT123\"}";
        return this;
    }
    
    /**
     * Configure as a car rental booking with default car details
     */
    public BookingTestDataBuilder asCarRentalBooking() {
        this.bookingType = "CAR";
        this.details = "{\"company\":\"Hertz\",\"carType\":\"sedan\",\"confirmationCode\":\"HRZ456\"}";
        return this;
    }
    
    /**
     * Configure for past dates (completed travel)
     */
    public BookingTestDataBuilder withPastDates() {
        this.startDate = LocalDate.now().minusDays(14);
        this.endDate = LocalDate.now().minusDays(7);
        return this;
    }
    
    /**
     * Configure for future dates
     */
    public BookingTestDataBuilder withFutureDates() {
        this.startDate = LocalDate.now().plusDays(30);
        this.endDate = LocalDate.now().plusDays(37);
        return this;
    }
    
    /**
     * Configure for current/ongoing travel
     */
    public BookingTestDataBuilder withCurrentDates() {
        this.startDate = LocalDate.now().minusDays(2);
        this.endDate = LocalDate.now().plusDays(5);
        return this;
    }
    
    /**
     * Build the Booking entity
     */
    public Booking build() {
        return Booking.builder()
                .id(id)
                .tenantId(tenantId)
                .userId(userId)
                .bookingType(bookingType)
                .destination(destination)
                .startDate(startDate)
                .endDate(endDate)
                .status(status)
                .totalAmount(totalAmount)
                .details(details)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .createdBy(createdBy)
                .updatedBy(updatedBy)
                .build();
    }
}