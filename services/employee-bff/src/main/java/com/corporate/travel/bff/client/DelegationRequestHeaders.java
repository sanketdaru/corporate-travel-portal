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
 *   <li>{@code Authorization} — delegation token scoped to {@code audience} (sub=actor)</li>
 *   <li>{@code X-Delegated-Subject} — human principal being acted for</li>
 *   <li>{@code X-Delegation-Id} — validated delegation record UUID</li>
 *   <li>{@code X-Actor-Token} — original actor JWT for audit chain reconstruction</li>
 *   <li>{@code X-Consent-Id}, {@code X-Delegation-Purpose} — when known</li>
 * </ul>
 * Otherwise only {@code Authorization} is set, with the caller's own token. Delegation headers
 * are never sent blank: downstream services use their presence to detect delegation mode.
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
            .header("X-Delegated-Subject", ctx.getSubjectId())
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
