package com.corporate.travel.bff.grant;

import com.corporate.travel.bff.config.BffProperties;
import com.corporate.travel.bff.exception.TokenExchangeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * OAuth 2.0 calls made with the {@code delegation-grant} client (ADR-024): building the
 * delegator's authorization request, redeeming the code, refreshing the offline token and
 * revoking it. Plain RFC 6749 / RFC 7009 form posts.
 */
@Component
@Slf4j
public class KeycloakGrantClient {

    /** Tokens returned by the token endpoint. {@code refreshToken} may be null on refresh (no rotation). */
    public record GrantTokens(String accessToken, String refreshToken) {}

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};

    private final BffProperties.Keycloak keycloak;
    private final BffProperties.Grant grant;
    private final RestClient restClient;

    public KeycloakGrantClient(BffProperties properties, ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder) {
        this.keycloak = properties.getKeycloak();
        this.grant = properties.getDelegation().getGrant();
        this.restClient = RestClient.builder()
            .requestFactory(requestFactoryBuilder.build())
            .baseUrl(keycloak.getUrl() + "/realms/" + keycloak.getRealm() + "/protocol/openid-connect")
            .build();
    }

    /**
     * Authorization request for the delegator: offline access plus Keycloak's parameterized
     * {@code delegation:user:<delegate>} scope, which yields {@code may_act} after consent.
     */
    public String authorizationUrl(String delegateId, String loginHint, String state, String codeChallenge) {
        String base = keycloak.getPublicUrl() != null ? keycloak.getPublicUrl() : keycloak.getUrl();
        return UriComponentsBuilder.fromUriString(base)
            .path("/realms/{realm}/protocol/openid-connect/auth")
            .queryParam("client_id", grant.getClientId())
            .queryParam("response_type", "code")
            .queryParam("scope", "openid offline_access delegation:user:" + delegateId)
            .queryParam("redirect_uri", grant.getRedirectUri())
            .queryParam("state", state)
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .queryParam("login_hint", loginHint)
            .encode()
            .buildAndExpand(keycloak.getRealm())
            .toUriString();
    }

    public GrantTokens redeemCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", grant.getRedirectUri());
        form.add("code_verifier", codeVerifier);
        return tokens(post("/token", form, "redeem authorization code"));
    }

    /** Refreshes the stored offline token; the access token carries the delegator's may_act claim. */
    public GrantTokens refresh(String offlineToken) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", offlineToken);
        return tokens(post("/token", form, "refresh delegator offline token"));
    }

    /** RFC 7009 revocation of the offline token, which ends Keycloak's offline session for it. */
    public void revoke(String offlineToken) {
        MultiValueMap<String, String> form = clientForm();
        form.add("token", offlineToken);
        form.add("token_type_hint", "refresh_token");
        post("/revoke", form, "revoke delegator offline token");
    }

    private MultiValueMap<String, String> clientForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", grant.getClientId());
        form.add("client_secret", grant.getClientSecret());
        return form;
    }

    private Map<String, Object> post(String path, MultiValueMap<String, String> form, String purpose) {
        try {
            return restClient.post().uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JSON_OBJECT);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            log.warn("Keycloak refused to {}: HTTP {} {}", purpose, status.value(), e.getResponseBodyAsString());
            throw new GrantRejectedException("Keycloak refused to " + purpose + ": " + e.getResponseBodyAsString(), status.is4xxClientError());
        }
    }

    private static GrantTokens tokens(Map<String, Object> body) {
        if (body == null || body.get("access_token") == null) {
            throw new TokenExchangeException("Keycloak token response has no access_token");
        }
        Object refresh = body.get("refresh_token");
        return new GrantTokens(body.get("access_token").toString(), refresh != null ? refresh.toString() : null);
    }

    /** Keycloak rejected a grant operation; {@code clientError} distinguishes invalid/revoked grants from outages. */
    public static class GrantRejectedException extends TokenExchangeException {
        private final boolean clientError;

        public GrantRejectedException(String message, boolean clientError) {
            super(message);
            this.clientError = clientError;
        }

        public boolean isClientError() {
            return clientError;
        }
    }
}
