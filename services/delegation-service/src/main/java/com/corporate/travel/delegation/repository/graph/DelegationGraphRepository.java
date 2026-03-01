package com.corporate.travel.delegation.repository.graph;

import com.corporate.travel.delegation.model.entity.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Neo4j Repository for User nodes and Delegation relationships
 * 
 * Provides graph traversal queries for delegation chains
 */
@Repository
public interface DelegationGraphRepository extends Neo4jRepository<UserNode, String> {

    /**
     * Find user node by user ID and tenant ID
     */
    Optional<UserNode> findByUserIdAndTenantId(String userId, String tenantId);

    /**
     * Find all users in a tenant
     */
    List<UserNode> findByTenantId(String tenantId);

    /**
     * Find delegation chain starting from a user
     * Returns all users this user can act as (direct and indirect)
     */
    @Query("MATCH path = (start:User {userId: $userId, tenantId: $tenantId})" +
           "-[:CAN_ACT_AS*1..3]->(end:User) " +
           "WHERE ALL(r IN relationships(path) WHERE r.active = true) " +
           "RETURN path")
    List<UserNode> findDelegationChain(
        @Param("userId") String userId,
        @Param("tenantId") String tenantId
    );

    /**
     * Find direct delegations for a user (one hop)
     */
    @Query("MATCH (delegator:User {userId: $userId, tenantId: $tenantId})" +
           "-[r:CAN_ACT_AS]->(delegate:User) " +
           "WHERE r.active = true " +
           "RETURN delegate")
    List<UserNode> findDirectDelegates(
        @Param("userId") String userId,
        @Param("tenantId") String tenantId
    );

    /**
     * Find all users who can act as this user (reverse lookup)
     */
    @Query("MATCH (delegate:User)-[r:CAN_ACT_AS]->" +
           "(delegator:User {userId: $userId, tenantId: $tenantId}) " +
           "WHERE r.active = true " +
           "RETURN delegate")
    List<UserNode> findWhoCanActAsUser(
        @Param("userId") String userId,
        @Param("tenantId") String tenantId
    );

    /**
     * Check if delegation path exists between two users
     */
    @Query("MATCH path = (start:User {userId: $fromUserId, tenantId: $tenantId})" +
           "-[:CAN_ACT_AS*1..3]->(end:User {userId: $toUserId, tenantId: $tenantId}) " +
           "WHERE ALL(r IN relationships(path) WHERE r.active = true) " +
           "RETURN COUNT(path) > 0")
    boolean delegationPathExists(
        @Param("fromUserId") String fromUserId,
        @Param("toUserId") String toUserId,
        @Param("tenantId") String tenantId
    );

    /**
     * Delete all relationships for a specific delegation ID
     */
    @Query("MATCH ()-[r:CAN_ACT_AS {delegationId: $delegationId}]->() DELETE r")
    void deleteDelegationRelationship(@Param("delegationId") String delegationId);

    /**
     * Update relationship active status
     */
    @Query("MATCH ()-[r:CAN_ACT_AS {delegationId: $delegationId}]->() " +
           "SET r.active = $active")
    void updateDelegationActiveStatus(
        @Param("delegationId") String delegationId,
        @Param("active") boolean active
    );
}