/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The state and the page to land on, carried through Discord in a cookie.
 */
class OAuthStateTest {

    @Test
    @DisplayName("The page to land on survives the trip, query and all")
    void roundTrip() {
        OAuthState started = new OAuthState("abc123", "/account/licenses?filter=a b&x=y");

        OAuthState back = OAuthState.decode(started.encode()).orElseThrow();

        assertEquals("abc123", back.state());
        assertEquals(OAuthState.DEFAULT_NEXT, back.next(), "a page with whitespace in it is not taken");
        OAuthState plain = OAuthState.decode(new OAuthState("s", "/account/security?x=1&y=2").encode())
                .orElseThrow();
        assertEquals("/account/security?x=1&y=2", plain.next());
    }

    @Test
    @DisplayName("The encoded value holds nothing a cookie cannot carry")
    void encodedIsCookieSafe() {
        String encoded = new OAuthState("abc", "/account?x=1;y=\"2\",z").encode();

        assertTrue(encoded.matches("[A-Za-z0-9._-]+"), encoded);
    }

    @Test
    @DisplayName("Without a page the account is where it lands")
    void defaultsToTheAccount() {
        assertEquals("/account", new OAuthState("s", null).next());
        assertEquals("/account", OAuthState.decode("only-a-state").orElseThrow().next());
        assertEquals(
                "only-a-state", OAuthState.decode("only-a-state").orElseThrow().state());
        assertEquals("/account", OAuthState.decode("s.%%%").orElseThrow().next());
    }

    @Test
    @DisplayName("No cookie, no state")
    void nothingDecodesToNothing() {
        assertFalse(OAuthState.decode(null).isPresent());
        assertFalse(OAuthState.decode(" ").isPresent());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"//example.invalid/", "https://example.invalid/", "/\\example.invalid", "account", "/a\nb", ""})
    @DisplayName("Anything but a path on this site is replaced")
    void foreignPagesAreRefused(String next) {
        assertEquals(OAuthState.DEFAULT_NEXT, OAuthState.localPath(next));
    }
}
