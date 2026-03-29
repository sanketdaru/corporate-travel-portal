package com.corporate.travel.bff.model;

import lombok.Value;

/**
 * Result of a consent validation check performed by ConsentServiceClient.
 *
 * <p>Carries both the validity flag and the consentId so callers can store
 * the consent reference in DelegationContext for downstream audit records (ADR-011).</p>
 */
@Value
public class ConsentCheckResult {

    /** Whether active consent covering the requested scopes was found. */
    boolean valid;

    /**
     * UUID of the matching consent record, or null if no consent was found.
     * Must be forwarded to downstream services as part of the delegation context
     * so audit tables can record consent_id per ADR-011.
     */
    String consentId;
}
