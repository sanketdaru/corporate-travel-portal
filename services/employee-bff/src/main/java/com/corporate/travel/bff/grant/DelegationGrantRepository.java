package com.corporate.travel.bff.grant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for delegation grants. Stores only ciphertext; encryption is the caller's job
 * ({@link GrantTokenCipher}).
 */
@Repository
public class DelegationGrantRepository {

    public record StoredGrant(UUID delegationId, String delegatorId, String delegateId, String encryptedOfflineToken) {}

    private final JdbcClient jdbc;

    public DelegationGrantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts or replaces the grant for a delegation (re-authorizing replaces the stored token). */
    public void save(StoredGrant grant) {
        int updated = jdbc.sql("""
                UPDATE bff.delegation_grant
                   SET delegator_id = :delegator, delegate_id = :delegate,
                       encrypted_offline_token = :token, updated_at = now()
                 WHERE delegation_id = :id
                """)
            .param("id", grant.delegationId()).param("delegator", grant.delegatorId())
            .param("delegate", grant.delegateId()).param("token", grant.encryptedOfflineToken())
            .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO bff.delegation_grant (delegation_id, delegator_id, delegate_id, encrypted_offline_token)
                    VALUES (:id, :delegator, :delegate, :token)
                    """)
                .param("id", grant.delegationId()).param("delegator", grant.delegatorId())
                .param("delegate", grant.delegateId()).param("token", grant.encryptedOfflineToken())
                .update();
        }
    }

    public Optional<StoredGrant> find(UUID delegationId) {
        return jdbc.sql("""
                SELECT delegation_id, delegator_id, delegate_id, encrypted_offline_token
                  FROM bff.delegation_grant WHERE delegation_id = :id
                """)
            .param("id", delegationId)
            .query((rs, row) -> new StoredGrant(
                rs.getObject("delegation_id", UUID.class), rs.getString("delegator_id"),
                rs.getString("delegate_id"), rs.getString("encrypted_offline_token")))
            .optional();
    }

    public void delete(UUID delegationId) {
        jdbc.sql("DELETE FROM bff.delegation_grant WHERE delegation_id = :id").param("id", delegationId).update();
    }
}
