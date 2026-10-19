/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AES-GCM over the stored tokens.
 */
class TokenCipherTest {
    private static TokenCipher cipher(byte fill) {
        byte[] raw = new byte[32];
        Arrays.fill(raw, fill);
        return new TokenCipher(new TokenKey(Base64.getEncoder().encodeToString(raw), Path.of("unused")));
    }

    @Test
    @DisplayName("What is sealed opens again to the same text")
    void roundTrip() throws Exception {
        TokenCipher cipher = cipher((byte) 1);

        byte[] sealed = cipher.seal("access-token-é", "discord_token:7");

        assertEquals("access-token-é", cipher.open(sealed, "discord_token:7"));
        assertFalse(new String(sealed, StandardCharsets.UTF_8).contains("access-token"));
    }

    @Test
    @DisplayName("Every seal has a nonce of its own, so the same text never looks the same twice")
    void noncesDiffer() {
        TokenCipher cipher = cipher((byte) 1);

        assertFalse(Arrays.equals(cipher.seal("same", "c"), cipher.seal("same", "c")));
    }

    @Test
    @DisplayName("Another key, another context or an altered byte does not open")
    void wrongInputsDoNotOpen() {
        TokenCipher cipher = cipher((byte) 1);
        byte[] sealed = cipher.seal("secret", "discord_token:1");
        byte[] altered = sealed.clone();
        altered[altered.length - 1] ^= 1;

        assertThrows(TokenCipher.Unreadable.class, () -> cipher((byte) 2).open(sealed, "discord_token:1"));
        assertThrows(TokenCipher.Unreadable.class, () -> cipher.open(sealed, "discord_token:2"));
        assertThrows(TokenCipher.Unreadable.class, () -> cipher.open(altered, "discord_token:1"));
        assertThrows(TokenCipher.Unreadable.class, () -> cipher.open(new byte[5], "discord_token:1"));
        assertThrows(TokenCipher.Unreadable.class, () -> cipher.open(null, "discord_token:1"));
    }
}
