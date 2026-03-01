package com.corporate.travel.consent.model.entity;

/**
 * Consent lifecycle status
 */
public enum ConsentStatus {
    ACTIVE,    // Consent is currently valid
    REVOKED,   // Consent was manually revoked
    EXPIRED    // Consent has expired based on expires_at timestamp
}