package com.corporate.travel.delegation.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for creating a new delegation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new delegation")
public class CreateDelegationRequest {

    @NotBlank(message = "Delegate ID is required")
    @Schema(description = "User ID receiving the delegation (e.g., Dave's user ID)", 
            example = "dave-assistant", required = true)
    private String delegateId;

    @NotBlank(message = "Purpose is required")
    @Size(max = 500, message = "Purpose must not exceed 500 characters")
    @Schema(description = "Business purpose for the delegation", 
            example = "book_travel", required = true)
    private String purpose;

    @NotEmpty(message = "At least one scope is required")
    @Schema(description = "List of specific permissions granted", 
            example = "[\"view_bookings\", \"create_bookings\"]", required = true)
    private List<String> scopes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Optional expiration timestamp for the delegation",
            example = "2026-12-31T23:59:59")
    private LocalDateTime expiresAt;
}