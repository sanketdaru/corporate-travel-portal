package com.corporate.travel.bff.grant;

import com.corporate.travel.bff.config.BffProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for delegators' offline refresh tokens at rest (ADR-024).
 *
 * <p>Format: base64(iv[12] || ciphertext+tag). The delegation id is bound as additional
 * authenticated data, so a ciphertext copied to another delegation fails to decrypt.</p>
 */
@Component
public class GrantTokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public GrantTokenCipher(BffProperties properties) {
        String encoded = properties.getDelegation().getGrant().getEncryptionKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("delegation.grant.encryption-key is not set");
        }
        byte[] raw = Base64.getDecoder().decode(encoded);
        if (raw.length != 32) {
            throw new IllegalStateException("delegation.grant.encryption-key must be 32 bytes (AES-256), got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext, String associatedData) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt grant token", e);
        }
    }

    public String decrypt(String encoded, String associatedData) {
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not decrypt grant token (wrong key or tampered data)", e);
        }
    }
}
