package com.corporate.travel.travel.controller;

import com.corporate.travel.security.SecurityConfig;
import com.corporate.travel.travel.exception.BookingNotFoundException;
import com.corporate.travel.travel.exception.GlobalExceptionHandler;
import com.corporate.travel.travel.service.BookingAuditService;
import com.corporate.travel.travel.service.BookingService;
import com.corporate.travel.travel.testutil.BookingTestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The internal budget endpoint accepts only the expense-service service account
 * (realm role {@code budget_reader}, ADR-009) and only within the requested tenant.
 * Tokens go through the real {@code JwtAuthenticationConverter}; only decoding is mocked.
 */
@WebMvcTest(BookingController.class)
@DisplayName("GET /api/bookings/{id}/budget security (ADR-009)")
class BookingBudgetEndpointSecurityTest {

    @Configuration
    @Import({BookingController.class, GlobalExceptionHandler.class, SecurityConfig.class})
    static class TestConfig {}

    private static final UUID BOOKING_ID = UUID.fromString("b1111111-1111-1111-1111-111111111111");
    private static final String TENANT = "tenant-a";
    private static final String PATH = "/api/bookings/" + BOOKING_ID + "/budget";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private BookingAuditService bookingAuditService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("service-token")).thenReturn(jwt("service-token", "service-account-expense-service",
            List.of("default-roles-corporate-travel", "budget_reader")));
        when(jwtDecoder.decode("user-token")).thenReturn(jwt("user-token", "alice.employee",
            List.of("default-roles-corporate-travel", "employee", "manager", "admin")));
    }

    @Test
    void serviceAccount_readsBudgetInTenant() throws Exception {
        when(bookingService.getBookingForTenant(BOOKING_ID, TENANT)).thenReturn(BookingTestDataBuilder.aBooking()
            .withId(BOOKING_ID).withTenantId(TENANT).withBudget(new BigDecimal("1500.00")).withBudgetCurrency("USD").build());

        mockMvc.perform(get(PATH).param("tenantId", TENANT).header("Authorization", "Bearer service-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budget").value(1500.00))
            .andExpect(jsonPath("$.budgetCurrency").value("USD"));
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mockMvc.perform(get(PATH).param("tenantId", TENANT))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(bookingService);
    }

    @Test
    void userTokenWithoutBudgetReaderRole_isForbidden() throws Exception {
        mockMvc.perform(get(PATH).param("tenantId", TENANT).header("Authorization", "Bearer user-token"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(bookingService);
    }

    @Test
    void bookingInOtherTenant_isNotFound() throws Exception {
        when(bookingService.getBookingForTenant(BOOKING_ID, "tenant-b")).thenThrow(new BookingNotFoundException(BOOKING_ID));

        mockMvc.perform(get(PATH).param("tenantId", "tenant-b").header("Authorization", "Bearer service-token"))
            .andExpect(status().isNotFound());
    }

    @Test
    void missingTenant_isBadRequest() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", "Bearer service-token"))
            .andExpect(status().isBadRequest());
        verify(bookingService, never()).getBookingForTenant(any(), any());
    }

    private static Jwt jwt(String value, String username, List<String> roles) {
        return Jwt.withTokenValue(value)
            .header("alg", "RS256")
            .subject(username)
            .claim("preferred_username", username)
            .claim("realm_access", Map.of("roles", roles))
            .build();
    }
}
