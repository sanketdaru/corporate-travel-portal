package com.corporate.travel.consent.model.dto;

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
@AllArgsConstructor
public class ValidateConsentResponse {

    private boolean valid;
    private UUID consentId;
    private String reason;  // Why validation failed (if applicable)
    private List<String> missingScopes;  // Scopes not covered by consent
}