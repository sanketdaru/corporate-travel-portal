package com.corporate.travel.consent.model.dto;

import lombok.AccessLevel;
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
// Package-private: Jackson 3 otherwise picks the public all-args constructor as creator,
// so omitted JSON fields become null instead of their @Builder.Default values.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
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