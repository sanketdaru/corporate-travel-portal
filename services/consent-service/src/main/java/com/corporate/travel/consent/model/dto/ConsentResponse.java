package com.corporate.travel.consent.model.dto;

import com.corporate.travel.consent.model.entity.ConsentStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response containing consent details
 */
@Data
@Builder
@NoArgsConstructor
// Package-private: Jackson 3 otherwise picks the public all-args constructor as creator,
// so omitted JSON fields become null instead of their @Builder.Default values.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class ConsentResponse {

    private UUID id;
    private String tenantId;
    private String grantorId;
    private String granteeId;
    private UUID delegationId;
    private String purpose;
    private List<String> scopes;
    private List<String> dataCategories;
    private LocalDateTime grantedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String revokedBy;
    private ConsentStatus status;
    private Map<String, Object> metadata;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private boolean valid;  // Computed: is consent currently valid?
}