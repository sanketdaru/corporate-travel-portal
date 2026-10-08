package com.corporate.travel.bff.client;

import com.corporate.travel.bff.model.DelegationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Applies the Authorization header and, when delegation is active, the delegation identity
 * headers (ADR-004, ADR-018, ADR-011) to a downstream request.
 *
 * <p>When {@code delegationContext} is present, the request carries:</p>
 * <ul>
 *   <li>{@code Authorization} — RFC 8693 delegated token scoped to {@code audience}
 *       ({@code sub}=delegator, {@code act.sub}=actor). The subject comes from the token, never a header.</li>
 *   <li>{@code X-Actor-Token} — the actor's own JWT; services verify it and require its {@code sub}
 *       to equal {@code act.sub}, which gives them the actor's username (ADR-024)</li>
 *   <li>{@code X-Delegation-Id} — delegation record UUID, for audit</li>
 *   <li>{@code X-Consent-Id}, {@code X-Delegation-Purpose} — when known, for audit and OPA</li>
 * </ul>
 * Otherwise only {@code Authorization} is set, with the caller's own token. Delegation headers
 * are never sent blank.
 */
final class DelegationRequestHeaders {

    private DelegationRequestHeaders() {
    }

    static RestClient.RequestHeadersSpec<?> apply(
            RestClient.RequestHeadersSpec<?> spec,
            String audience,
            String userToken,
            Optional<DelegationContext> delegationContext) {

        if (delegationContext.isEmpty()) {
            return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken);
        }
        DelegationContext ctx = delegationContext.get();
        spec = spec
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + ctx.tokenFor(audience))
            .header("X-Delegation-Id", ctx.getDelegationId())
            .header("X-Actor-Token", ctx.getActorToken());
        if (ctx.getConsentId() != null) {
            spec = spec.header("X-Consent-Id", ctx.getConsentId());
        }
        if (ctx.getPurpose() != null) {
            spec = spec.header("X-Delegation-Purpose", ctx.getPurpose());
        }
        return spec;
    }
}
