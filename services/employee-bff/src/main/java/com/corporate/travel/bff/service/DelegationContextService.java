package com.corporate.travel.bff.service;

import com.corporate.travel.bff.model.DelegationContext;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Manages the active delegation context stored in the user's HTTP session.
 * When a user activates delegation mode, the issued delegation token is stored
 * here and used for all downstream calls until deactivated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DelegationContextService {

    static final String SESSION_KEY = "ACTIVE_DELEGATION_CONTEXT";

    private final TokenExchangeService tokenExchangeService;

    /**
     * Activates delegation mode for the current session.
     * Performs token exchange and stores the resulting DelegationContext in the session.
     *
     * @param delegationId   Delegation ID to activate
     * @param actorToken     Actor's Bearer token (Dave's JWT) — used as subject_token
     * @param actorId        Actor's user ID
     * @param targetAudience Target resource server audience
     * @param session        Current HTTP session
     * @return The activated DelegationContext
     */
    public DelegationContext activateDelegation(
            String delegationId,
            String actorToken,
            String actorId,
            String targetAudience,
            HttpSession session) {

        DelegationContext context = tokenExchangeService.exchangeForDelegation(
            delegationId, actorToken, actorId, targetAudience);

        session.setAttribute(SESSION_KEY, context);
        log.info("Delegation activated: actor={}, subject={}, audience={}",
            context.getActorId(), context.getSubjectId(), context.getAudience());

        return context;
    }

    /**
     * Deactivates delegation mode by removing the context from the session.
     */
    public void deactivateDelegation(HttpSession session) {
        DelegationContext removed = (DelegationContext) session.getAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY);
        if (removed != null) {
            log.info("Delegation deactivated: actor={}, subject={}", removed.getActorId(), removed.getSubjectId());
        }
    }

    /**
     * Returns the active delegation context if one exists and is not expired.
     */
    public Optional<DelegationContext> getActiveContext(HttpSession session) {
        DelegationContext context = (DelegationContext) session.getAttribute(SESSION_KEY);
        if (context == null) {
            return Optional.empty();
        }
        if (context.getExpiresAt() != null && Instant.now().isAfter(context.getExpiresAt())) {
            log.info("Delegation token expired, removing from session");
            session.removeAttribute(SESSION_KEY);
            return Optional.empty();
        }
        return Optional.of(context);
    }
}
