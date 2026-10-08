package com.corporate.travel.bff.grant;

import com.corporate.travel.bff.client.DelegationServiceClient;
import com.corporate.travel.bff.config.BffProperties;
import com.corporate.travel.bff.exception.DelegationNotFoundException;
import com.corporate.travel.bff.exception.TokenExchangeException;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keycloak-native delegation grants (ADR-024).
 *
 * <p>The delegator (Carol) authorizes a delegation once, by signing in through the
 * {@code delegation-grant} client with {@code offline_access delegation:user:<delegate>} and
 * approving Keycloak's consent screen. The resulting offline refresh token — which carries
 * {@code may_act} on every refresh — is stored encrypted. When the delegate (Dave) activates the
 * delegation, the BFF refreshes it to get a delegator access token for the RFC 8693 exchange.</p>
 */
@Service
@Slf4j
public class DelegationGrantService {

    static final String PENDING_GRANTS = "PENDING_DELEGATION_GRANTS";

    /** Authorization request in flight, held in the delegator's BFF session until the callback. */
    record PendingGrant(String delegationId, String delegatorId, String delegateId, String codeVerifier)
        implements java.io.Serializable {}

    private final DelegationServiceClient delegationServiceClient;
    private final KeycloakGrantClient keycloakGrantClient;
    private final DelegationGrantRepository repository;
    private final GrantTokenCipher cipher;
    private final ObjectMapper objectMapper;
    private final BffProperties.Grant grantProperties;
    private final SecureRandom random = new SecureRandom();

    public DelegationGrantService(DelegationServiceClient delegationServiceClient,
                                  KeycloakGrantClient keycloakGrantClient,
                                  DelegationGrantRepository repository,
                                  GrantTokenCipher cipher,
                                  ObjectMapper objectMapper,
                                  BffProperties properties) {
        this.delegationServiceClient = delegationServiceClient;
        this.keycloakGrantClient = keycloakGrantClient;
        this.repository = repository;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.grantProperties = properties.getDelegation().getGrant();
    }

    /**
     * Starts the delegator's grant sign-in. Only the delegator of an active delegation may start it.
     *
     * @return the Keycloak authorization URL the browser must navigate to
     */
    public String startGrant(String delegationId, String callerId, String callerToken, HttpSession session) {
        JsonNode delegation = requireDelegation(delegationId, callerToken);
        String delegatorId = delegation.path("delegatorId").asString();
        String delegateId = delegation.path("delegateId").asString();
        if (!callerId.equals(delegatorId)) {
            throw new AccessDeniedException("Only the delegator can authorize delegation " + delegationId);
        }
        if (!isActive(delegation)) {
            throw new IllegalArgumentException("Delegation " + delegationId + " is not active");
        }

        String state = randomUrlSafe(32);
        String verifier = randomUrlSafe(48);
        pendingGrants(session).put(state, new PendingGrant(delegationId, delegatorId, delegateId, verifier));
        log.info("Delegation grant started: delegation={}, delegator={}, delegate={}", delegationId, delegatorId, delegateId);
        return keycloakGrantClient.authorizationUrl(delegateId, delegatorId, state, s256(verifier));
    }

    /**
     * Completes the grant from Keycloak's redirect and returns the frontend URL to send the browser to.
     * Never throws for expected failures: the outcome is reported to the frontend as a query parameter.
     */
    public String completeGrant(String code, String state, String error, HttpSession session) {
        PendingGrant pending = state == null ? null : pendingGrants(session).remove(state);
        if (pending == null) {
            log.warn("Delegation grant callback with unknown state");
            return returnUrl("error", null, "unknown_or_expired_request");
        }
        if (error != null || code == null) {
            log.info("Delegation grant not completed: delegation={}, error={}", pending.delegationId(), error);
            return returnUrl("error", pending.delegationId(), error != null ? error : "no_code");
        }

        KeycloakGrantClient.GrantTokens tokens;
        try {
            tokens = keycloakGrantClient.redeemCode(code, pending.codeVerifier());
        } catch (TokenExchangeException e) {
            return returnUrl("error", pending.delegationId(), "code_redemption_failed");
        }
        JsonNode claims = payload(tokens.accessToken());

        // The account that signed in must be the delegator, and Keycloak must have honoured the
        // delegation scope (it drops it silently when FGAP denies the pair).
        String signedIn = claims.path("preferred_username").asString("");
        boolean mayAct = claims.path("may_act").path("sub").isString();
        if (!pending.delegatorId().equals(signedIn) || !mayAct || tokens.refreshToken() == null) {
            log.warn("Delegation grant rejected: delegation={}, signedIn={}, mayAct={}", pending.delegationId(), signedIn, mayAct);
            if (tokens.refreshToken() != null) {
                revokeQuietly(tokens.refreshToken());
            }
            return returnUrl("error", pending.delegationId(), !mayAct ? "delegation_not_permitted" : "wrong_account");
        }

        repository.save(new DelegationGrantRepository.StoredGrant(
            UUID.fromString(pending.delegationId()), pending.delegatorId(), pending.delegateId(),
            cipher.encrypt(tokens.refreshToken(), pending.delegationId())));
        log.info("Delegation grant stored: delegation={}, delegator={}, delegate={}",
            pending.delegationId(), pending.delegatorId(), pending.delegateId());
        return returnUrl("success", pending.delegationId(), null);
    }

    public boolean isAuthorized(String delegationId) {
        return repository.find(UUID.fromString(delegationId)).isPresent();
    }

    /**
     * Returns a fresh delegator access token carrying {@code may_act}, for the delegated exchange.
     * Grants of revoked or expired delegations, and grants Keycloak no longer honours, are removed.
     */
    public String delegatorAccessToken(JsonNode delegation) {
        String delegationId = delegation.path("id").asString();
        Optional<DelegationGrantRepository.StoredGrant> stored = repository.find(UUID.fromString(delegationId));
        if (stored.isEmpty()) {
            throw new DelegationNotAuthorizedException(delegationId);
        }
        String offlineToken = cipher.decrypt(stored.get().encryptedOfflineToken(), delegationId);
        if (!isActive(delegation)) {
            // Backstop: revocation normally goes through revoke(), but expiry has no event
            revokeQuietly(offlineToken);
            repository.delete(UUID.fromString(delegationId));
            throw new IllegalArgumentException("Delegation " + delegationId + " is no longer active");
        }
        try {
            KeycloakGrantClient.GrantTokens tokens = keycloakGrantClient.refresh(offlineToken);
            if (tokens.refreshToken() != null && !tokens.refreshToken().equals(offlineToken)) {
                repository.save(new DelegationGrantRepository.StoredGrant(UUID.fromString(delegationId),
                    stored.get().delegatorId(), stored.get().delegateId(), cipher.encrypt(tokens.refreshToken(), delegationId)));
            }
            return tokens.accessToken();
        } catch (KeycloakGrantClient.GrantRejectedException e) {
            if (e.isClientError()) {
                // Offline session gone (revoked in Keycloak, idle-expired, user disabled)
                repository.delete(UUID.fromString(delegationId));
                throw new DelegationNotAuthorizedException(delegationId);
            }
            throw e;
        }
    }

    /** Revokes the delegation (delegator only, enforced by delegation-service) and its Keycloak grant. */
    public void revoke(String delegationId, String callerToken) {
        delegationServiceClient.revokeDelegation(delegationId, callerToken);
        repository.find(UUID.fromString(delegationId)).ifPresent(grant -> {
            revokeQuietly(cipher.decrypt(grant.encryptedOfflineToken(), delegationId));
            repository.delete(UUID.fromString(delegationId));
        });
        log.info("Delegation revoked with its Keycloak grant: delegation={}", delegationId);
    }

    private JsonNode requireDelegation(String delegationId, String token) {
        JsonNode delegation = delegationServiceClient.getDelegation(delegationId, token);
        if (delegation == null) {
            throw new DelegationNotFoundException(delegationId);
        }
        return delegation;
    }

    private static boolean isActive(JsonNode delegation) {
        return delegation.path("active").asBoolean(false) && delegation.path("valid").asBoolean(true);
    }

    private void revokeQuietly(String offlineToken) {
        try {
            keycloakGrantClient.revoke(offlineToken);
        } catch (RuntimeException e) {
            log.warn("Could not revoke offline token at Keycloak: {}", e.getMessage());
        }
    }

    /** Claims of a token received directly from Keycloak's token endpoint over the back channel. */
    private JsonNode payload(String jwt) {
        String[] parts = jwt.split("\\.");
        return objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }

    private String returnUrl(String outcome, String delegationId, String reason) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(grantProperties.getReturnUrl())
            .queryParam("grant", outcome);
        if (delegationId != null) {
            url.queryParam("delegationId", delegationId);
        }
        if (reason != null) {
            url.queryParam("reason", reason);
        }
        return url.encode().toUriString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, PendingGrant> pendingGrants(HttpSession session) {
        Map<String, PendingGrant> pending = (Map<String, PendingGrant>) session.getAttribute(PENDING_GRANTS);
        if (pending == null) {
            pending = new HashMap<>();
            session.setAttribute(PENDING_GRANTS, pending);
        }
        return pending;
    }

    private String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The delegator has not (or no longer) authorized this delegation in Keycloak. */
    public static class DelegationNotAuthorizedException extends RuntimeException {
        public DelegationNotAuthorizedException(String delegationId) {
            super("Delegation " + delegationId + " has not been authorized by the delegator in Keycloak");
        }
    }
}
