/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.entity.DiscordToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordTokensRepositoryTest extends RepositoryTestBase {
    private Account account;

    @BeforeEach
    void freshAccount() throws SQLException {
        clear("discord_token", "account_session", "account_identity", "account");
        account = accountService.register("discord-token@example.invalid", "hash");
    }

    @Test
    @DisplayName("A grant is kept as it was given, bytes and all")
    void saveAndFind() {
        Instant expires = Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.SECONDS);

        discordTokens.save(account.id(), new byte[] {1, 2, 3}, new byte[] {4, 5}, "identify email", expires);

        DiscordToken token = discordTokens.find(account.id()).orElseThrow();
        assertArrayEquals(new byte[] {1, 2, 3}, token.accessToken());
        assertArrayEquals(new byte[] {4, 5}, token.refreshToken());
        assertEquals("identify email", token.scope());
        assertEquals(expires, token.expiresAt());
        assertTrue(token.refreshedAt().isAfter(Instant.now().minus(Duration.ofMinutes(1))));
    }

    @Test
    @DisplayName("A new grant replaces the old one, so an account holds one at most")
    void saveReplaces() throws SQLException {
        discordTokens.save(account.id(), new byte[] {1}, new byte[] {1}, "identify", Instant.now());
        execute("UPDATE %s.discord_token SET refreshed_at = now() - INTERVAL '1 day'");

        discordTokens.save(
                account.id(),
                new byte[] {2},
                new byte[] {3},
                "identify email",
                Instant.now().plus(Duration.ofDays(7)));

        DiscordToken token = discordTokens.find(account.id()).orElseThrow();
        assertArrayEquals(new byte[] {2}, token.accessToken());
        assertArrayEquals(new byte[] {3}, token.refreshToken());
        assertTrue(token.refreshedAt().isAfter(Instant.now().minus(Duration.ofMinutes(1))));
        assertEquals(1, countRows("discord_token"));
    }

    @Test
    @DisplayName("Only grants running out before the moment are due, soonest first")
    void expiringBefore() {
        Account second = accountService.register("second@example.invalid", "hash");
        Account later = accountService.register("later@example.invalid", "hash");
        discordTokens.save(
                account.id(), new byte[] {1}, new byte[] {1}, "s", Instant.now().plus(Duration.ofHours(30)));
        discordTokens.save(
                second.id(), new byte[] {1}, new byte[] {1}, "s", Instant.now().plus(Duration.ofHours(2)));
        discordTokens.save(
                later.id(), new byte[] {1}, new byte[] {1}, "s", Instant.now().plus(Duration.ofDays(6)));

        List<Integer> due = discordTokens.expiringBefore(Instant.now().plus(Duration.ofHours(48))).stream()
                .map(DiscordToken::accountId)
                .toList();

        assertEquals(List.of(second.id(), account.id()), due);
    }

    @Test
    @DisplayName("Forgetting a grant says whether there was one")
    void delete() {
        discordTokens.save(account.id(), new byte[] {1}, new byte[] {1}, "s", Instant.now());

        assertTrue(discordTokens.delete(account.id()));
        assertFalse(discordTokens.delete(account.id()));
        assertTrue(discordTokens.find(account.id()).isEmpty());
    }

    @Test
    @DisplayName("Deleting the account takes its grant with it")
    void cascadesWithTheAccount() {
        discordTokens.save(account.id(), new byte[] {1}, new byte[] {1}, "s", Instant.now());

        accounts.delete(account.id());

        assertTrue(discordTokens.find(account.id()).isEmpty());
    }
}
