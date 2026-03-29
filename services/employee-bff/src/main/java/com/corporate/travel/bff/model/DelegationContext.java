package com.corporate.travel.bff.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Represents an active delegation session — the result of a successful Standard Token Exchange V2.
 * Stored in HttpSession for the duration of the delegation mode.
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

    /** The delegation token issued by Keycloak (sub=Carol, act.sub=Dave) */
    private String delegationToken;

    /** When the delegation token expires */
    private Instant expiresAt;
}
