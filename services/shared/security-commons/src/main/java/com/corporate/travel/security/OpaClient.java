package com.corporate.travel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with Open Policy Agent (OPA)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OpaClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${opa.url:http://opa:8181}")
    private String opaUrl;

    /**
     * Evaluate authorization policy
     */
    public boolean authorize(SecurityContext context, String action, Map<String, Object> resource) {
        try {
            Map<String, Object> input = buildOpaInput(context, action, resource);
            
            Map<String, Object> request = Map.of("input", input);

            WebClient webClient = webClientBuilder.baseUrl(opaUrl).build();
            
            Map<String, Object> response = webClient.post()
                    .uri("/v1/data/corporate/travel/authorization/allow")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("result")) {
                Boolean allowed = (Boolean) response.get("result");
                log.debug("OPA authorization decision: {} for action: {}", allowed, action);
                return Boolean.TRUE.equals(allowed);
            }

            log.warn("OPA returned unexpected response: {}", response);
            return false;

        } catch (Exception e) {
            log.error("Error calling OPA for authorization", e);
            return false;  // Fail closed
        }
    }

    private Map<String, Object> buildOpaInput(SecurityContext context, String action, Map<String, Object> resource) {
        Map<String, Object> input = new HashMap<>();

        // User context
        Map<String, Object> user = new HashMap<>();
        user.put("user_id", context.getUserId());
        user.put("tenant_id", context.getTenantId());
        user.put("roles", context.getRoles());
        input.put("user", user);

        // Action
        input.put("action", action);

        // Resource
        input.put("resource", resource);

        // Delegation context
        Map<String, Object> delegation = new HashMap<>();
        delegation.put("active", context.isDelegated());
        if (context.isDelegated()) {
            delegation.put("delegate_id", context.getActorId());
            delegation.put("delegator_id", context.getSubjectId());
        }
        input.put("delegation", delegation);

        // Consent context
        Map<String, Object> consent = new HashMap<>();
        if (context.getConsentId() != null) {
            consent.put("valid", true);
            consent.put("consent_id", context.getConsentId());
            consent.put("scopes", extractScopesFromPurpose(context.getPurpose()));
        } else {
            consent.put("valid", false);
            consent.put("scopes", List.of());
        }
        input.put("consent", consent);

        return input;
    }

    private List<String> extractScopesFromPurpose(String purpose) {
        // Map purpose to scopes - this is simplified
        if (purpose == null) {
            return List.of();
        }
        return switch (purpose) {
            case "book_travel" -> List.of("book_travel", "view_booking");
            case "approve_expenses" -> List.of("approve_expenses", "view_expense");
            case "manage_team" -> List.of("view_booking", "view_expense", "approve_expenses");
            default -> List.of();
        };
    }
}
