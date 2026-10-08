package com.corporate.travel.bff.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Active delegation state held in the actor's HTTP session.
 *
 * <p>Tokens are never serialized to API responses: the browser only needs to know that
 * delegation is active and for whom, not the backend-scoped credentials.</p>
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

    /** Resource servers a delegation token was exchanged for (e.g. travel-service, expense-service) */
    private List<String> audiences;

    /** The delegation purpose (e.g. "book_travel"). Forwarded as X-Delegation-Purpose. */
    private String purpose;

    /**
     * Audience-scoped tokens issued by Keycloak, keyed by audience. Each downstream service
     * validates {@code aud}, so a travel-service token cannot be replayed against expense-service.
     */
    @JsonIgnore
    private Map<String, String> delegationTokens;

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

    /** When the earliest of the delegation tokens expires */
    private Instant expiresAt;

    /**
     * Returns the delegation token scoped to {@code audience}.
     *
     * @throws IllegalStateException if no token was exchanged for that audience — a configuration
     *         error (see {@code delegation.audiences}), not a client error
     */
    public String tokenFor(String audience) {
        String token = delegationTokens == null ? null : delegationTokens.get(audience);
        if (token == null) {
            throw new IllegalStateException("No delegation token exchanged for audience " + audience
                + "; add it to delegation.audiences");
        }
        return token;
    }
}
