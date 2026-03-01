package com.corporate.travel.consent.service;

import com.corporate.travel.consent.model.dto.*;
import com.corporate.travel.security.SecurityContext;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for consent management
 */
public interface ConsentService {

    /**
     * Grant a new consent
     */
    ConsentResponse grantConsent(CreateConsentRequest request, SecurityContext context);

    /**
     * Get consent by ID
     */
    ConsentResponse getConsent(UUID id, SecurityContext context);

    /**
     * List all consents granted by the current user
     */
    List<ConsentResponse> getMyConsents(SecurityContext context);

    /**
     * List all consents granted to the current user
     */
    List<ConsentResponse> getConsentsToMe(SecurityContext context);

    /**
     * Revoke a consent
     */
    void revokeConsent(UUID id, SecurityContext context);

    /**
     * Validate if consent exists for specific action/scopes
     */
    ValidateConsentResponse validateConsent(ValidateConsentRequest request, SecurityContext context);

    /**
     * Get audit trail for a consent
     */
    List<ConsentAuditResponse> getConsentAuditTrail(UUID consentId, SecurityContext context);
}