package com.corporate.travel.travel.testutil;

import com.corporate.travel.models.BookingStatus;
import com.corporate.travel.travel.model.entity.Booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Static factory methods for common booking test scenarios
 * Provides pre-configured test fixtures for typical use cases
 */
public class BookingTestFixtures {
    
    // Test user IDs
    public static final String ALICE_USER_ID = "alice.employee";
    public static final String BOB_USER_ID = "bob.manager";
    public static final String CAROL_USER_ID = "carol.executive";
    public static final String DAVE_USER_ID = "dave.assistant";
    public static final String EVE_USER_ID = "eve.employee";
    
    // Test tenant IDs
    public static final String TENANT_A = "tenant-a";
    public static final String TENANT_B = "tenant-b";
    
    // Test booking IDs
    public static final UUID BOOKING_ID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID BOOKING_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID BOOKING_ID_3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    
    /**
     * Create a valid pending booking for Alice in Tenant A
     */
    public static Booking validPendingBookingForAlice() {
        return BookingTestDataBuilder.aBooking()
                .withId(BOOKING_ID_1)
                .withTenantId(TENANT_A)
                .withUserId(ALICE_USER_ID)
                .withCreatedBy(ALICE_USER_ID)
                .withUpdatedBy(ALICE_USER_ID)
                .withStatus(BookingStatus.PENDING)
                .asFlightBooking()
                .build();
    }
    
    /**
     * Create a confirmed booking for Bob in Tenant A
     */
    public static Booking confirmedBookingForBob() {
        return BookingTestDataBuilder.aBooking()
                .withId(BOOKING_ID_2)
                .withTenantId(TENANT_A)
                .withUserId(BOB_USER_ID)
                .withCreatedBy(BOB_USER_ID)
                .withUpdatedBy(BOB_USER_ID)
                .withStatus(BookingStatus.CONFIRMED)
                .asHotelBooking()
                .withDestination("San Francisco")
                .withTotalAmount(new BigDecimal("2500.00"))
                .build();
    }
    
    /**
     * Create a cancelled booking for Carol in Tenant A
     */
    public static Booking cancelledBookingForCarol() {
        return BookingTestDataBuilder.aBooking()
                .withId(BOOKING_ID_3)
                .withTenantId(TENANT_A)
                .withUserId(CAROL_USER_ID)
                .withCreatedBy(CAROL_USER_ID)
                .withUpdatedBy(CAROL_USER_ID)
                .withStatus(BookingStatus.CANCELLED)
                .asCarRentalBooking()
                .withPastDates()
                .build();
    }
    
    /**
     * Create a booking in Tenant B for Eve
     */
    public static Booking bookingForEveInTenantB() {
        return BookingTestDataBuilder.aBooking()
                .withTenantId(TENANT_B)
                .withUserId(EVE_USER_ID)
                .withCreatedBy(EVE_USER_ID)
                .withUpdatedBy(EVE_USER_ID)
                .withStatus(BookingStatus.PENDING)
                .asFlightBooking()
                .build();
    }
    
    /**
     * Create a delegated booking where Dave created it on behalf of Carol
     */
    public static Booking delegatedBookingDaveForCarol() {
        return BookingTestDataBuilder.aBooking()
                .withTenantId(TENANT_A)
                .withUserId(CAROL_USER_ID)  // Carol is the owner
                .withCreatedBy(DAVE_USER_ID)  // Dave created it
                .withUpdatedBy(DAVE_USER_ID)
                .withStatus(BookingStatus.PENDING)
                .asFlightBooking()
                .withDestination("London")
                .withTotalAmount(new BigDecimal("3500.00"))
                .build();
    }
    
    /**
     * Create multiple bookings for a single tenant
     */
    public static List<Booking> multipleBookingsForTenantA() {
        List<Booking> bookings = new ArrayList<>();
        bookings.add(validPendingBookingForAlice());
        bookings.add(confirmedBookingForBob());
        bookings.add(cancelledBookingForCarol());
        return bookings;
    }
    
    /**
     * Create a booking with null/minimal fields for testing validation
     */
    public static Booking bookingWithMinimalFields() {
        return Booking.builder()
                .tenantId(TENANT_A)
                .userId(ALICE_USER_ID)
                .bookingType("FLIGHT")
                .status(BookingStatus.PENDING)
                .build();
    }
    
    /**
     * Create bookings with different statuses for parameterized tests
     */
    public static List<Booking> bookingsWithAllStatuses() {
        List<Booking> bookings = new ArrayList<>();
        for (BookingStatus status : BookingStatus.values()) {
            bookings.add(BookingTestDataBuilder.aBooking()
                    .withTenantId(TENANT_A)
                    .withUserId(ALICE_USER_ID)
                    .withStatus(status)
                    .build());
        }
        return bookings;
    }
    
    /**
     * Create bookings with different booking types
     */
    public static List<Booking> bookingsWithDifferentTypes() {
        List<Booking> bookings = new ArrayList<>();
        bookings.add(BookingTestDataBuilder.aBooking().asFlightBooking().build());
        bookings.add(BookingTestDataBuilder.aBooking().asHotelBooking().build());
        bookings.add(BookingTestDataBuilder.aBooking().asCarRentalBooking().build());
        return bookings;
    }
}