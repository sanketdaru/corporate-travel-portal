package com.corporate.travel.delegation.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

/**
 * UserNode - Neo4j Graph Node
 * 
 * Represents a user in the delegation graph.
 * Users can have CAN_ACT_AS relationships to other users.
 */
@Node("User")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNode {

    @Id
    private String userId;  // Keycloak user ID

    @Property("tenantId")
    private String tenantId;

    @Property("email")
    private String email;

    @Property("displayName")
    private String displayName;

    @Relationship(type = "CAN_ACT_AS", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private Set<DelegationRelationship> delegations = new HashSet<>();

    /**
     * Add a delegation relationship
     */
    public void addDelegation(UserNode delegate, DelegationRelationship relationship) {
        relationship.setDelegate(delegate);
        delegations.add(relationship);
    }

    /**
     * Remove a delegation relationship
     */
    public void removeDelegation(String delegationId) {
        delegations.removeIf(rel -> rel.getDelegationId().equals(delegationId));
    }
}