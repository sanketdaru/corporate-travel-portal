package com.corporate.travel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the actor of an RFC 8693 delegated token (ADR-024).
 *
 * <p>Keycloak's {@code act} claim names the actor only by user id. Services key users by username,
 * so the BFF forwards the actor's own JWT as {@code X-Actor-Token}. This filter verifies that JWT
 * with the service's {@link JwtDecoder} (signature, issuer, expiry, audience) and requires its
 * {@code sub} to equal {@code act.sub} and its tenant to match the delegator's. The verified actor
 * JWT is exposed as request attribute {@link #ACTOR_ATTRIBUTE}: authorization decisions use the
 * actor's identity and roles, never the delegator's (no privilege gain through delegation).</p>
 *
 * <p>Requests whose token has no {@code act} claim are not delegated, whatever headers they carry.</p>
 */
@Slf4j
public class DelegatedActorFilter extends OncePerRequestFilter {

    /** Verified actor {@link Jwt} of a delegated request. */
    public static final String ACTOR_ATTRIBUTE = DelegatedActorFilter.class.getName() + ".actor";
    static final String ACTOR_TOKEN_HEADER = "X-Actor-Token";

    private final JwtDecoder jwtDecoder;

    public DelegatedActorFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth) || !(jwtAuth.getToken().getClaim("act") instanceof Map<?, ?> act)) {
            chain.doFilter(request, response);
            return;
        }

        Jwt delegated = jwtAuth.getToken();
        String actorToken = request.getHeader(ACTOR_TOKEN_HEADER);
        if (!StringUtils.hasText(actorToken)) {
            reject(response, "Delegated token without " + ACTOR_TOKEN_HEADER);
            return;
        }
        Jwt actor;
        try {
            actor = jwtDecoder.decode(actorToken);
        } catch (JwtException e) {
            reject(response, ACTOR_TOKEN_HEADER + " is not a valid token: " + e.getMessage());
            return;
        }
        if (!Objects.equals(actor.getSubject(), act.get("sub"))) {
            reject(response, ACTOR_TOKEN_HEADER + " does not belong to the actor named in act.sub");
            return;
        }
        if (!Objects.equals(actor.getClaimAsString("tenant_id"), delegated.getClaimAsString("tenant_id"))) {
            reject(response, "Actor and delegator belong to different tenants");
            return;
        }
        if (actor.hasClaim("act")) {
            reject(response, ACTOR_TOKEN_HEADER + " must be the actor's own token, not a delegated one");
            return;
        }

        request.setAttribute(ACTOR_ATTRIBUTE, actor);
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, String reason) throws IOException {
        log.warn("Rejected delegated request: {}", reason);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"invalid_delegation\",\"message\":\"" + reason.replace("\"", "'") + "\"}");
    }
}
