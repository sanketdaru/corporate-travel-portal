package com.corporate.travel.consent.model.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response for consent validation
 */
@Data
@Builder
@NoArgsConstructor
// Package-private: Jackson 3 otherwise picks the public all-args constructor as creator,
// so omitted JSON fields become null instead of their @Builder.Default values.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class ValidateConsentResponse {

    private boolean valid;
    private UUID consentId;
    private String reason;  // Why validation failed (if applicable)
    private List<String> missingScopes;  // Scopes not covered by consent
}