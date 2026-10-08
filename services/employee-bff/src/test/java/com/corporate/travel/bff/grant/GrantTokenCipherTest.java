package com.corporate.travel.bff.grant;

import com.corporate.travel.bff.config.BffProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantTokenCipherTest {

    private static GrantTokenCipher cipher(String key) {
        BffProperties properties = new BffProperties();
        properties.getDelegation().getGrant().setEncryptionKey(key);
        return new GrantTokenCipher(properties);
    }

    private final GrantTokenCipher cipher = cipher(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void roundTrip() {
        String encrypted = cipher.encrypt("offline-refresh-token", "delegation-1");

        assertThat(encrypted).doesNotContain("offline-refresh-token");
        assertThat(cipher.decrypt(encrypted, "delegation-1")).isEqualTo("offline-refresh-token");
    }

    @Test
    void sameTokenEncryptsDifferentlyEachTime() {
        assertThat(cipher.encrypt("t", "d")).isNotEqualTo(cipher.encrypt("t", "d"));
    }

    @Test
    void ciphertextIsBoundToItsDelegation() {
        String encrypted = cipher.encrypt("offline-refresh-token", "delegation-1");

        assertThatThrownBy(() -> cipher.decrypt(encrypted, "delegation-2"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyCannotDecrypt() {
        String encrypted = cipher.encrypt("offline-refresh-token", "delegation-1");
        byte[] other = new byte[32];
        other[0] = 1;

        assertThatThrownBy(() -> cipher(Base64.getEncoder().encodeToString(other)).decrypt(encrypted, "delegation-1"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeysThatAreNotAes256() {
        assertThatThrownBy(() -> cipher(Base64.getEncoder().encodeToString(new byte[16])))
            .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> cipher(""))
            .hasMessageContaining("not set");
    }
}
