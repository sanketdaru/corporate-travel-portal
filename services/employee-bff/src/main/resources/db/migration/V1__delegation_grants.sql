-- Delegation grants (ADR-024).
--
-- One row per delegation the delegator has authorized in Keycloak. The offline refresh
-- token lets the BFF obtain a delegator access token carrying may_act when the delegate
-- activates the delegation later. It is AES-256-GCM encrypted (iv || ciphertext, base64),
-- with the delegation id as additional authenticated data, so a ciphertext cannot be moved
-- to another row.
CREATE TABLE delegation_grant (
    delegation_id           UUID         PRIMARY KEY,
    delegator_id            VARCHAR(255) NOT NULL,
    delegate_id             VARCHAR(255) NOT NULL,
    encrypted_offline_token TEXT         NOT NULL,
    granted_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_delegation_grant_delegator ON delegation_grant (delegator_id);
