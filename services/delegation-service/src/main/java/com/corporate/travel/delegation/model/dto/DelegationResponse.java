package com.corporate.travel.delegation.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for delegation operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Delegation details")
public class DelegationResponse {

    @Schema(description = "Unique delegation ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Tenant ID", example = "tenant-a")
    private String tenantId;

    @Schema(description = "User ID granting the delegation", example = "carol-executive")
    private String delegatorId;

    @Schema(description = "User ID receiving the delegation", example = "dave-assistant")
    private String delegateId;

    @Schema(description = "Business purpose for the delegation", example = "book_travel")
    private String purpose;

    @Schema(description = "List of specific permissions granted", 
            example = "[\"view_bookings\", \"create_bookings\"]")
    private List<String> scopes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the delegation was granted", example = "2026-02-28T10:00:00")
    private LocalDateTime grantedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Optional expiration timestamp", example = "2026-12-31T23:59:59")
    private LocalDateTime expiresAt;

    @Schema(description = "Whether the delegation is currently active", example = "true")
    private Boolean active;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the delegation was revoked (if applicable)")
    private LocalDateTime revokedAt;

    @Schema(description = "User ID who revoked the delegation (if applicable)")
    private String revokedBy;

    @Schema(description = "Whether the delegation is currently valid (active and not expired)", example = "true")
    private Boolean valid;

    @Schema(description = "User ID who created the delegation", example = "carol-executive")
    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the delegation was created", example = "2026-02-28T10:00:00")
    private LocalDateTime createdAt;
}