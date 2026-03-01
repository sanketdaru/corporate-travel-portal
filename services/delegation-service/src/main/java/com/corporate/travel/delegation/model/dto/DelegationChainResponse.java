package com.corporate.travel.delegation.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for delegation chain queries (graph traversal)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Delegation chain showing the path of delegations")
public class DelegationChainResponse {

    @Schema(description = "Starting user ID", example = "dave-assistant")
    private String startUserId;

    @Schema(description = "List of delegation nodes in the chain")
    private List<DelegationNode> chain;

    @Schema(description = "Total number of hops in the chain", example = "2")
    private Integer depth;

    /**
     * Represents a single node in the delegation chain
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "A node in the delegation chain")
    public static class DelegationNode {

        @Schema(description = "User ID at this node", example = "carol-executive")
        private String userId;

        @Schema(description = "User display name", example = "Carol Executive")
        private String displayName;

        @Schema(description = "Delegation ID connecting to this node")
        private String delegationId;

        @Schema(description = "Purpose of this delegation", example = "book_travel")
        private String purpose;

        @Schema(description = "Scopes granted in this delegation")
        private List<String> scopes;

        @Schema(description = "Whether this delegation is active", example = "true")
        private Boolean active;
    }
}
