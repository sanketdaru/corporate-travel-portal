package com.corporate.travel.travel.testutil;

import com.corporate.travel.models.BookingStatus;
import com.corporate.travel.travel.model.entity.Booking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Static factory methods for common booking test scenarios.
 * Provides pre-configured travel authorization fixtures for typical use cases.
 */
public class BookingTestFixtures {

    public static final String ALICE_USER_ID = "alice.employee";
    public static final String BOB_USER_ID   = "bob.manager";
    public static final String CAROL_USER_ID = "carol.executive";
    public static final String DAVE_USER_ID  = "dave.assistant";
    public static final String EVE_USER_ID   = "eve.employee";

    public static final String TENANT_A = "tenant-a";
    public static final String TENANT_B = "tenant-b";

    public static final UUID BOOKING_ID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID BOOKING_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID BOOKING_ID_3 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** Pending travel authorization for Alice — London client meetings. */
    public static Booking validPendingBookingForAlice() {
        return BookingTestDataBuilder.aBooking()
                .withId(BOOKING_ID_1)
                .withTenantId(TENANT_A)
                .withUserId(ALICE_USER_ID)
                .withCreatedBy(ALICE_USER_ID)
                .withUpdatedBy(ALICE_USER_ID)
                .withDestination("London, UK")
                .withBusinessPurpose("Q2 client review meetings")
                .withBudget(new BigDecimal("200000.00"))
                .withStatus(BookingStatus.PENDING)
                .build();
    }

    /** Confirmed travel authorization for Bob — San Francisco conference. */
    public static Booking confirmedBookingForBob() {
        return BookingTestDataBuilder.aBooking()
                .withId(BOOKING_ID_2)
                .withTenantId(TENANT_A)
                .withUserId(BOB_USER_ID)
                .withCreatedBy(BOB_USER_ID)
                .withUpdatedBy(BOB_USER_ID)
                .withDestination("San Francisco, USA")
                .withBusinessPurpose("Annual leadership summit")
                .withBudget(new BigDecimal("250000.00"))
                .withStatus(BookingStatus.CONFIRMED)
                .build();
    }

    /** Cancelled travel authorization for Carol — past dates. */
    public static Booking cancelledBookingForCarol() {
        return BookingTestDataBuilder.aBooking()
                .withId(BOOKING_ID_3)
                .withTenantId(TENANT_A)
                .withUserId(CAROL_USER_ID)
                .withCreatedBy(CAROL_USER_ID)
                .withUpdatedBy(CAROL_USER_ID)
                .withDestination("Berlin, Germany")
                .withBusinessPurpose("European partner summit")
                .withBudget(new BigDecimal("180000.00"))
                .withStatus(BookingStatus.CANCELLED)
                .withPastDates()
                .build();
    }

    /** Pending authorization for Eve in Tenant B — Singapore. */
    public static Booking bookingForEveInTenantB() {
        return BookingTestDataBuilder.aBooking()
                .withTenantId(TENANT_B)
                .withUserId(EVE_USER_ID)
                .withCreatedBy(EVE_USER_ID)
                .withUpdatedBy(EVE_USER_ID)
                .withDestination("Singapore")
                .withBusinessPurpose("APAC partnership negotiations")
                .withBudget(new BigDecimal("120000.00"))
                .withStatus(BookingStatus.PENDING)
                .build();
    }

    /** Delegated authorization — Dave created it on behalf of Carol. */
    public static Booking delegatedBookingDaveForCarol() {
        return BookingTestDataBuilder.aBooking()
                .withTenantId(TENANT_A)
                .withUserId(CAROL_USER_ID)   // Carol is the owner (subject)
                .withCreatedBy(DAVE_USER_ID) // Dave created it (actor)
                .withUpdatedBy(DAVE_USER_ID)
                .withDestination("Shanghai, China")
                .withBusinessPurpose("APAC executive summit")
                .withBudget(new BigDecimal("350000.00"))
                .withStatus(BookingStatus.PENDING)
                .build();
    }

    /** Multiple authorizations for Tenant A used in list/filter tests. */
    public static List<Booking> multipleBookingsForTenantA() {
        List<Booking> bookings = new ArrayList<>();
        bookings.add(validPendingBookingForAlice());
        bookings.add(confirmedBookingForBob());
        bookings.add(cancelledBookingForCarol());
        return bookings;
    }

    /** Minimal valid booking for testing required-field validation. */
    public static Booking bookingWithMinimalFields() {
        return Booking.builder()
                .tenantId(TENANT_A)
                .userId(ALICE_USER_ID)
                .destination("Tokyo, Japan")
                .startDate(java.time.LocalDate.now().plusDays(10))
                .endDate(java.time.LocalDate.now().plusDays(17))
                .status(BookingStatus.PENDING)
                .budget(new BigDecimal("100000.00"))
                .budgetCurrency("INR")
                .build();
    }

    /** Authorizations with all possible statuses for parameterized tests. */
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
}
