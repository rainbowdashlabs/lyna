/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.entity.AccountSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountSessionsRepositoryTest extends RepositoryTestBase {
    private Account account;

    @BeforeEach
    void freshAccount() throws SQLException {
        clear("account_session", "account_identity", "account");
        account = accountService.register("sessions@example.invalid", "hash");
    }

    @Test
    @DisplayName("A recorded session is found again by its token id")
    void recordAndFind() {
        Instant expiresAt = Instant.now().plus(Duration.ofHours(24));

        accountSessions.record("jti-1", account.id(), expiresAt, "Firefox");

        AccountSession found = accountSessions.find("jti-1").orElseThrow();
        assertEquals("jti-1", found.jti());
        assertEquals(account.id(), found.accountId());
        assertEquals("Firefox", found.userAgent());
        assertNotNull(found.issuedAt());
        assertNull(found.lastSeenAt());
    }

    @Test
    @DisplayName("Nothing is found for a token id that was never recorded")
    void findMissing() {
        assertTrue(accountSessions.find("never-issued").isEmpty());
    }

    @Test
    @DisplayName("A session with no expiry left is no longer an active one")
    void expiredSessionsAreNotActive() {
        accountSessions.record("live", account.id(), Instant.now().plus(Duration.ofHours(1)), "Firefox");
        accountSessions.record("stale", account.id(), Instant.now().minus(Duration.ofHours(1)), "Chrome");

        List<AccountSession> active = accountSessions.activeForAccount(account.id());

        assertEquals(1, active.size());
        assertEquals("live", active.getFirst().jti());
    }

    @Test
    @DisplayName("Only the account's own sessions are listed")
    void activeIsScopedToTheAccount() {
        Account other = accountService.register("other@example.invalid", "hash");
        accountSessions.record("mine", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.record("theirs", other.id(), Instant.now().plus(Duration.ofHours(1)), null);

        assertEquals(
                List.of("mine"),
                accountSessions.activeForAccount(account.id()).stream()
                        .map(AccountSession::jti)
                        .toList());
        assertEquals(
                List.of("theirs"),
                accountSessions.activeForAccount(other.id()).stream()
                        .map(AccountSession::jti)
                        .toList());
    }

    @Test
    @DisplayName("Being seen again stamps a live session with the time")
    void touchIfLiveStampsTheSession() {
        accountSessions.record("seen", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        assertNull(accountSessions.find("seen").orElseThrow().lastSeenAt());

        assertTrue(accountSessions.touchIfLive("seen"));

        assertNotNull(accountSessions.find("seen").orElseThrow().lastSeenAt());
    }

    @Test
    @DisplayName("Being seen twice within a minute writes the time once")
    void touchIsThrottled() throws SQLException {
        accountSessions.record("busy", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.touchIfLive("busy");
        Instant first = accountSessions.find("busy").orElseThrow().lastSeenAt();

        assertTrue(accountSessions.touchIfLive("busy"));
        assertEquals(first, accountSessions.find("busy").orElseThrow().lastSeenAt());

        execute("UPDATE %s.account_session SET last_seen_at = now() - INTERVAL '2 minutes' WHERE jti = 'busy'");
        Instant stale = accountSessions.find("busy").orElseThrow().lastSeenAt();
        assertTrue(accountSessions.touchIfLive("busy"));
        assertTrue(accountSessions.find("busy").orElseThrow().lastSeenAt().isAfter(stale));
    }

    @Test
    @DisplayName("A revoked, an expired and an unknown session are none of them live")
    void onlyLiveSessionsAreLive() {
        accountSessions.record("ended", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.revoke("ended");
        accountSessions.record("lapsed", account.id(), Instant.now().minus(Duration.ofHours(1)), null);

        assertFalse(accountSessions.touchIfLive("ended"));
        assertFalse(accountSessions.touchIfLive("lapsed"));
        assertFalse(accountSessions.touchIfLive("never-issued"));
        assertNull(accountSessions.find("ended").orElseThrow().lastSeenAt());
    }

    @Test
    @DisplayName("Revoking ends the session once and keeps the row, so it can say when")
    void revokeKeepsTheRow() {
        accountSessions.record("out", account.id(), Instant.now().plus(Duration.ofHours(1)), null);

        assertTrue(accountSessions.revoke("out"));
        assertFalse(accountSessions.revoke("out"), "a second revocation has nothing left to end");

        AccountSession ended = accountSessions.find("out").orElseThrow();
        assertNotNull(ended.revokedAt());
        assertTrue(accountSessions.activeForAccount(account.id()).isEmpty());
    }

    @Test
    @DisplayName("Signing out on one device leaves the account's other devices signed in")
    void revokeIsPerDevice() {
        accountSessions.record("laptop", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.record("phone", account.id(), Instant.now().plus(Duration.ofHours(1)), null);

        accountSessions.revoke("laptop");

        assertFalse(accountSessions.touchIfLive("laptop"));
        assertTrue(accountSessions.touchIfLive("phone"));
    }

    @Test
    @DisplayName("Ending the other sessions keeps the one named and leaves other accounts alone")
    void revokeOthersKeepsTheCurrentOne() {
        Account other = accountService.register("bystander@example.invalid", "hash");
        accountSessions.record("current", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.record("phone", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.record("desktop", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.record("theirs", other.id(), Instant.now().plus(Duration.ofHours(1)), null);

        assertEquals(2, accountSessions.revokeOthers(account.id(), "current"));

        assertEquals(
                List.of("current"),
                accountSessions.activeForAccount(account.id()).stream()
                        .map(AccountSession::jti)
                        .toList());
        assertTrue(accountSessions.touchIfLive("theirs"));
    }

    @Test
    @DisplayName("Renewing moves a live session's expiry and leaves an ended one ended")
    void renewMovesTheExpiry() {
        Instant later = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS);
        accountSessions.record("kept", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.record("gone", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.revoke("gone");

        assertTrue(accountSessions.renew("kept", later));
        assertFalse(accountSessions.renew("gone", later));

        assertEquals(later, accountSessions.find("kept").orElseThrow().expiresAt());
        assertFalse(accountSessions.touchIfLive("gone"));
    }

    @Test
    @DisplayName("Signing out everywhere leaves the account with no sessions")
    void deleteAllForAccount() {
        accountSessions.record("one", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.record("two", account.id(), Instant.now().plus(Duration.ofHours(1)), null);

        accountSessions.deleteAllForAccount(account.id());

        assertTrue(accountSessions.activeForAccount(account.id()).isEmpty());
        assertTrue(accountSessions.find("one").isEmpty());
    }

    @Test
    @DisplayName("Deleting an account takes its sessions with it")
    void deletingAccountRemovesSessions() {
        accountSessions.record("doomed", account.id(), Instant.now().plus(Duration.ofHours(1)), null);

        accounts.delete(account.id());

        assertTrue(accountSessions.find("doomed").isEmpty());
    }
}
