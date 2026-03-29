package com.corporate.travel.bff.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Represents an active delegation session — the result of a successful Standard Token Exchange V2.
 * Stored in HttpSession for the duration of the delegation mode.
 *
 * <p>Fields actorToken and consentId are required to thread the full delegation identity context
 * (ADR-004 Layer 2 headers, ADR-011 audit fields) on every downstream call while delegation is active.</p>
 */
@Data
@Builder
public class DelegationContext {

    /** ID of the delegation record in delegation-service */
    private String delegationId;

    /** The actor performing actions (e.g. Dave's user ID) */
    private String actorId;

    /** The subject being acted on behalf of (e.g. Carol's user ID) */
    private String subjectId;

    /** The audience this delegation token is scoped to (e.g. "travel-service") */
    private String audience;

    /** The audience-scoped delegation token issued by Keycloak (sub=actor, aud=target-service) */
    private String delegationToken;

    /**
     * The actor's original JWT prior to the exchange. Threaded as X-Actor-Token header on every
     * downstream delegated call (ADR-004). Never serialized to API responses.
     */
    @JsonIgnore
    private String actorToken;

    /**
     * UUID of the consent record that authorised this delegation. Forwarded so that downstream
     * services can record consent_id in their audit tables (ADR-011).
     */
    private String consentId;

    /** When the delegation token expires */
    private Instant expiresAt;
}
