package com.corporate.travel.security;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Security context extracted from JWT token
 */
@Data
@Builder
public class SecurityContext {
    private String userId;
    private String username;
    private String tenantId;
    private List<String> roles;
    private Map<String, Object> attributes;
    
    // Delegation context
    private boolean isDelegated;
    private String actorId;  // Who is actually performing the action
    private String subjectId;  // On whose behalf the action is performed
    
    // Consent context
    private String consentId;
    private String purpose;
}
