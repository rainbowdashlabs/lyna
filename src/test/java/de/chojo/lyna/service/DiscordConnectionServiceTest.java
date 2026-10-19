/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.auth.DiscordOAuthClient;
import de.chojo.lyna.auth.TokenCipher;
import de.chojo.lyna.auth.TokenKey;
import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.entity.AccountEmail;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.entity.DiscordToken;
import de.chojo.lyna.feature.account.service.DiscordConnectionService;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Keeping the Discord grant of an account alive, and what Discord says about the person with it.
 */
class DiscordConnectionServiceTest extends RepositoryTestBase {
    private static final long DISCORD_ID = 777_000_111L;

    private TokenCipher cipher;
    private DiscordOAuthClient discord;
    private DiscordConnectionService connections;
    private Account account;

    private static TokenCipher cipher(byte fill) {
        byte[] raw = new byte[32];
        Arrays.fill(raw, fill);
        return new TokenCipher(new TokenKey(Base64.getEncoder().encodeToString(raw), Path.of("unused")));
    }

    @BeforeEach
    void fresh() throws SQLException {
        clear("discord_token", "account_email", "account_session", "account_identity", "account");
        cipher = cipher((byte) 3);
        discord = Mockito.mock(DiscordOAuthClient.class);
        connections = new DiscordConnectionService(discordTokens, cipher, discord, accountLinks, accountEmailService);
        account = accountService.register(null, null);
        accountLinks.link(account.id(), DISCORD_ID, AccountIdentity.Verification.OAUTH, "old-handle");
    }

    private static DiscordOAuthClient.Grant grant(String access, String refresh, long expiresIn) {
        return new DiscordOAuthClient.Grant(access, refresh, "identify email", expiresIn);
    }

    private static DiscordOAuthClient.DiscordUser user(long id, String handle, String email, boolean verified) {
        return new DiscordOAuthClient.DiscordUser(id, handle, null, email, verified);
    }

    private String refreshTokenOf(int accountId) throws Exception {
        DiscordToken token = discordTokens.find(accountId).orElseThrow();
        return cipher.open(token.refreshToken(), "discord_token:" + accountId);
    }

    @Test
    @DisplayName("A grant is kept encrypted, readable only under this account")
    void storeEncrypts() throws Exception {
        connections.store(account.id(), grant("access-1", "refresh-1", 604_800));

        DiscordToken token = discordTokens.find(account.id()).orElseThrow();
        assertFalse(new String(token.accessToken()).contains("access-1"));
        assertEquals("access-1", cipher.open(token.accessToken(), "discord_token:" + account.id()));
        assertEquals("refresh-1", refreshTokenOf(account.id()));
        assertTrue(token.expiresAt().isAfter(Instant.now().plus(Duration.ofDays(6))));
    }

    @Test
    @DisplayName("A grant without a refresh token cannot be kept alive, and is not kept")
    void storeNeedsARefreshToken() {
        connections.store(account.id(), grant("a", null, 3600));
        connections.store(account.id(), grant("a", " ", 3600));

        assertTrue(discordTokens.find(account.id()).isEmpty());
    }

    @Test
    @DisplayName("Unlinking forgets the grant")
    void forget() {
        connections.store(account.id(), grant("a", "r", 3600));

        connections.forget(account.id());

        assertTrue(discordTokens.find(account.id()).isEmpty());
    }

    @Test
    @DisplayName("A grant running out within two days is refreshed, the rotated pair written in the same step")
    void expiringGrantIsRefreshed() throws Exception {
        connections.store(account.id(), grant("a", "old-refresh", 3600));
        when(discord.refresh("old-refresh")).thenReturn(grant("new-access", "new-refresh", 604_800));
        when(discord.fetchUser("new-access"))
                .thenReturn(user(DISCORD_ID, "new-handle", "proved@example.invalid", true));

        assertEquals(1, connections.refreshExpiring());

        assertEquals("new-refresh", refreshTokenOf(account.id()));
        assertTrue(discordTokens
                .find(account.id())
                .orElseThrow()
                .expiresAt()
                .isAfter(Instant.now().plus(Duration.ofDays(6))));
        assertEquals(
                "new-handle",
                accountLinks.discordIdentity(account.id()).orElseThrow().handle());
        assertTrue(accountEmails.of(account.id()).stream()
                .filter(AccountEmail::verified)
                .anyMatch(email -> email.email().equals("proved@example.invalid")));
    }

    @Test
    @DisplayName("A grant with time to spare is left alone")
    void freshGrantIsLeftAlone() throws Exception {
        connections.store(account.id(), grant("a", "r", 604_800));

        assertEquals(0, connections.refreshExpiring());

        verify(discord, never()).refresh(anyString());
    }

    @Test
    @DisplayName("A grant Discord no longer honours is dropped")
    void invalidGrantIsDropped() throws Exception {
        connections.store(account.id(), grant("a", "revoked", 3600));
        when(discord.refresh("revoked")).thenThrow(new DiscordOAuthClient.InvalidGrantException("invalid_grant"));

        assertEquals(0, connections.refreshExpiring());

        assertTrue(discordTokens.find(account.id()).isEmpty());
    }

    @Test
    @DisplayName("A grant that cannot be decrypted with the current key is dropped")
    void undecryptableGrantIsDropped() throws Exception {
        DiscordConnectionService before = new DiscordConnectionService(
                discordTokens, cipher((byte) 9), discord, accountLinks, accountEmailService);
        before.store(account.id(), grant("a", "r", 3600));

        assertEquals(0, connections.refreshExpiring());

        assertTrue(discordTokens.find(account.id()).isEmpty());
        verify(discord, never()).refresh(anyString());
    }

    @Test
    @DisplayName("Discord being unreachable keeps the grant for the next run")
    void failureKeepsTheGrant() throws Exception {
        connections.store(account.id(), grant("a", "r", 3600));
        when(discord.refresh("r")).thenThrow(new java.io.IOException("unreachable"));

        assertEquals(0, connections.refreshExpiring());

        assertEquals("r", refreshTokenOf(account.id()));
    }

    @Test
    @DisplayName("A grant whose account is no longer linked to Discord is dropped unasked")
    void unlinkedAccountLosesItsGrant() throws Exception {
        connections.store(account.id(), grant("a", "r", 3600));
        accountLinks.unlink(account.id());

        assertEquals(0, connections.refreshExpiring());

        assertTrue(discordTokens.find(account.id()).isEmpty());
        verify(discord, never()).refresh(anyString());
    }

    @Test
    @DisplayName("A grant that turns out to name somebody else is dropped, and nothing of theirs is taken on")
    void foreignUserIsDropped() throws Exception {
        connections.store(account.id(), grant("a", "r", 3600));
        when(discord.refresh("r")).thenReturn(grant("x", "y", 604_800));
        when(discord.fetchUser("x")).thenReturn(user(1L, "someone-else", "other@example.invalid", true));

        connections.refreshExpiring();

        assertTrue(discordTokens.find(account.id()).isEmpty());
        assertEquals(
                "old-handle",
                accountLinks.discordIdentity(account.id()).orElseThrow().handle());
        assertTrue(accountEmails.of(account.id()).isEmpty());
    }

    @Test
    @DisplayName("Not being able to read the person again still keeps the refreshed grant")
    void userReadFailureKeepsTheGrant() throws Exception {
        connections.store(account.id(), grant("a", "r", 3600));
        when(discord.refresh("r")).thenReturn(grant("x", "y", 604_800));
        when(discord.fetchUser("x")).thenThrow(new DiscordOAuthClient.OAuthException("down"));

        assertEquals(1, connections.refreshExpiring());

        assertEquals("y", refreshTokenOf(account.id()));
    }

    @Test
    @DisplayName("Only an address Discord verified is taken on, and one held elsewhere is left there")
    void rememberTakesOnlyProvedAddresses() {
        Account holder = accountService.register("held@example.invalid", "hash");
        accountEmailService.confirm(holder.id(), "held@example.invalid");

        connections.remember(account.id(), user(DISCORD_ID, "h", "typed@example.invalid", false));
        connections.remember(account.id(), user(DISCORD_ID, "h", "held@example.invalid", true));

        assertTrue(accountEmails.of(account.id()).isEmpty());
        assertEquals(
                "h", accountLinks.discordIdentity(account.id()).orElseThrow().handle());
    }

    @Test
    @DisplayName("Every due grant is refreshed in one run")
    void refreshesEveryDueGrant() throws Exception {
        Account second = accountService.register(null, null);
        accountLinks.link(second.id(), DISCORD_ID + 1, AccountIdentity.Verification.OAUTH, "second");
        connections.store(account.id(), grant("a1", "r1", 3600));
        connections.store(second.id(), grant("a2", "r2", 3600));
        when(discord.refresh(anyString())).thenReturn(grant("n", "m", 604_800));
        when(discord.fetchUser("n")).thenThrow(new DiscordOAuthClient.OAuthException("skip"));

        assertEquals(2, connections.refreshExpiring());

        assertArrayEquals(
                new String[] {"m", "m"}, new String[] {refreshTokenOf(account.id()), refreshTokenOf(second.id())});
    }
}
