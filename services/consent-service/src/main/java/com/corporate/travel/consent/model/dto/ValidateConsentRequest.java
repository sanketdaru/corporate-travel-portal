package com.corporate.travel.consent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to validate if consent exists for specific action/scopes
 */
@Data
@Builder
@NoArgsConstructor
// Package-private: Jackson 3 otherwise picks the public all-args constructor as creator,
// so omitted JSON fields become null instead of their @Builder.Default values.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class ValidateConsentRequest {

    @NotBlank(message = "Grantor ID is required")
    private String grantorId;  // Person who gave consent

    @NotBlank(message = "Grantee ID is required")
    private String granteeId;  // Person who received consent

    @NotBlank(message = "Purpose is required")
    private String purpose;

    @NotEmpty(message = "At least one scope is required")
    private List<String> scopes;
}