/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.auth;

import de.chojo.lyna.auth.DiscordOAuthClient.DiscordUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What arrives with somebody signing in through Discord.
 *
 * <p>The address is the part worth being careful about. Proving one here collects the licences bought
 * with it, so an address Discord has not verified must not count - otherwise anybody could type a
 * stranger's address into Discord and sign in to collect their purchases.
 */
class DiscordUserTest {

    @Test
    @DisplayName("An address Discord has verified is one to go on")
    void verifiedCounts() {
        var user = new DiscordUser(1L, "ada", "Ada", "ada@example.invalid", true);

        assertEquals("ada@example.invalid", user.provedEmail().orElseThrow());
    }

    @Test
    @DisplayName("An address Discord has not verified counts for nothing")
    void unverifiedDoesNot() {
        var user = new DiscordUser(1L, "ada", "Ada", "typed-in@example.invalid", false);

        assertTrue(user.provedEmail().isEmpty(), "anybody can type an address they do not own");
    }

    @Test
    @DisplayName("No address at all is no address, however it is spelled")
    void absentIsAbsent() {
        assertTrue(new DiscordUser(1L, "ada", "Ada", null, true).provedEmail().isEmpty());
        assertTrue(new DiscordUser(1L, "ada", "Ada", "   ", true).provedEmail().isEmpty());
    }

    @Test
    @DisplayName("The name is the handle, falling back to the display name for an older account")
    void theNameIsTheHandle() {
        assertEquals("ada", new DiscordUser(1L, "ada", "Ada Lovelace", null, false).handle());
        assertEquals("Ada Lovelace", new DiscordUser(1L, null, "Ada Lovelace", null, false).handle());
        assertEquals(null, new DiscordUser(1L, "  ", "  ", null, false).handle());
    }
}
