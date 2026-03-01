package com.corporate.travel.delegation.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DelegationRelationship - Neo4j Graph Relationship
 * 
 * Represents a CAN_ACT_AS relationship between two users in the delegation graph.
 * Contains all the delegation metadata as relationship properties.
 */
@RelationshipProperties
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelegationRelationship {

    @Id
    @GeneratedValue
    private Long id;

    @Property("delegationId")
    private String delegationId;  // UUID from PostgreSQL

    @Property("grantedAt")
    private LocalDateTime grantedAt;

    @Property("expiresAt")
    private LocalDateTime expiresAt;

    @Property("purpose")
    private String purpose;

    @Property("scopes")
    private List<String> scopes;

    @Property("active")
    private Boolean active;

    @TargetNode
    private UserNode delegate;

    /**
     * Check if relationship is currently valid
     */
    public boolean isValid() {
        if (!active) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }
}