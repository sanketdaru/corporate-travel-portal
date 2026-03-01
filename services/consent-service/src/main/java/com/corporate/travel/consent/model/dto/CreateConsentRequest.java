package com.corporate.travel.consent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request to create a new consent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConsentRequest {

    @NotBlank(message = "Grantor ID is required")
    private String grantorId;  // Person giving consent

    @NotBlank(message = "Grantee ID is required")
    private String granteeId;  // Person receiving consent

    private UUID delegationId;  // Optional link to delegation

    @NotBlank(message = "Purpose is required")
    private String purpose;  // e.g., "book_travel", "approve_expenses"

    @NotEmpty(message = "At least one scope is required")
    private List<String> scopes;  // e.g., ["view_bookings", "create_bookings"]

    private List<String> dataCategories;  // e.g., ["travel_data", "expense_data"]

    private LocalDateTime expiresAt;  // Optional expiration

    private Map<String, Object> metadata;  // Additional context
}