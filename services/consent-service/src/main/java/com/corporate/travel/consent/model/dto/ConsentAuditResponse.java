package com.corporate.travel.consent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response containing consent audit record
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentAuditResponse {

    private UUID id;
    private UUID consentId;
    private String action;
    private String actorId;
    private String subjectId;
    private LocalDateTime timestamp;
    private Map<String, Object> details;
    private String tenantId;
}