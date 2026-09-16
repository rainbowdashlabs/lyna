package de.chojo.lyna.repository;

import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.AccountSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountSessionsRepositoryTest extends RepositoryTestBase {
    private Account account;

    @BeforeEach
    void freshAccount() throws SQLException {
        clear("account_session", "account_identity", "account");
        account = accounts.create("sessions@example.invalid", "hash");
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
        Account other = accounts.create("other@example.invalid", "hash");
        accountSessions.record("mine", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        accountSessions.record("theirs", other.id(), Instant.now().plus(Duration.ofHours(1)), null);

        assertEquals(List.of("mine"), accountSessions.activeForAccount(account.id()).stream()
                .map(AccountSession::jti).toList());
        assertEquals(List.of("theirs"), accountSessions.activeForAccount(other.id()).stream()
                .map(AccountSession::jti).toList());
    }

    @Test
    @DisplayName("Being seen again stamps the session with the time")
    void touchLastSeen() {
        accountSessions.record("seen", account.id(), Instant.now().plus(Duration.ofHours(1)), null);
        assertNull(accountSessions.find("seen").orElseThrow().lastSeenAt());

        accountSessions.touchLastSeen("seen");

        assertNotNull(accountSessions.find("seen").orElseThrow().lastSeenAt());
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
