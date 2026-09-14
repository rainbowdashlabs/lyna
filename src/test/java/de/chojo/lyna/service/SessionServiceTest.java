package de.chojo.lyna.service;

import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.configuration.elements.Auth;
import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.AccountSession;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The signing in, staying signed in and signing out that a browser session is made of, across the
 * token service and the three tables that remember what it issued.
 */
class SessionServiceTest extends RepositoryTestBase {
    private JwtService jwt;
    private Account account;

    @BeforeEach
    void freshSession() throws SQLException {
        clear("account_session", "revoked_jti", "account_discord_link", "account");
        Auth config = Mockito.mock(Auth.class);
        Mockito.when(config.jwtSecret()).thenReturn("service-test-secret");
        Mockito.when(config.jwtExpirySeconds()).thenReturn(3600L);
        jwt = new JwtService(config);
        account = accounts.create("session@example.invalid", "hash");
    }

    /**
     * @return the token of a freshly signed-in session, recorded the way the login endpoint records it
     */
    private JwtService.Issued signIn(String userAgent) {
        JwtService.Issued issued = jwt.issue(account.id(), null);
        accountSessions.record(issued.jti(), account.id(), issued.expiresAt(), userAgent);
        return issued;
    }

    @Test
    @DisplayName("Signing in leaves a token that verifies and a session that is listed")
    void signInIssuesAVerifiableToken() {
        JwtService.Issued issued = signIn("Firefox");

        JwtService.Verified verified = jwt.verify(issued.token()).orElseThrow();
        assertEquals(account.id(), verified.accountId());
        assertEquals(issued.jti(), verified.jti());

        assertEquals(1, accountSessions.activeForAccount(account.id()).size());
        assertFalse(revokedJtis.isRevoked(issued.jti()));
    }

    @Test
    @DisplayName("Signing out revokes the token, so a still-valid one stops being accepted")
    void signOutRevokesTheToken() {
        JwtService.Issued issued = signIn("Firefox");

        revokedJtis.revoke(issued.jti(), issued.expiresAt());

        assertTrue(jwt.verify(issued.token()).isPresent());
        assertTrue(revokedJtis.isRevoked(issued.jti()));
    }

    @Test
    @DisplayName("Ending every other session leaves the one being used")
    void endOtherSessionsKeepsTheCurrentOne() {
        JwtService.Issued current = signIn("Firefox");
        JwtService.Issued phone = signIn("Safari on iOS");
        JwtService.Issued desktop = signIn("Chrome");

        for (AccountSession session : accountSessions.activeForAccount(account.id())) {
            if (session.jti().equals(current.jti())) continue;
            revokedJtis.revoke(session.jti(), session.expiresAt());
        }

        assertFalse(revokedJtis.isRevoked(current.jti()));
        assertTrue(revokedJtis.isRevoked(phone.jti()));
        assertTrue(revokedJtis.isRevoked(desktop.jti()));
    }

    @Test
    @DisplayName("A token signed with another instance's secret is not accepted")
    void tokenFromAnotherInstanceIsRefused() {
        Auth foreign = Mockito.mock(Auth.class);
        Mockito.when(foreign.jwtSecret()).thenReturn("a-different-secret");
        Mockito.when(foreign.jwtExpirySeconds()).thenReturn(3600L);
        JwtService.Issued issued = new JwtService(foreign).issue(account.id(), null);

        assertTrue(jwt.verify(issued.token()).isEmpty());
    }

    @Test
    @DisplayName("Deleting the account takes its sessions, so nothing it signed stays listed")
    void deletingTheAccountEndsItsSessions() {
        JwtService.Issued issued = signIn("Firefox");

        accountSessions.deleteAllForAccount(account.id());
        accounts.delete(account.id());

        assertTrue(accountSessions.find(issued.jti()).isEmpty());
        Optional<JwtService.Verified> verified = jwt.verify(issued.token());
        assertTrue(verified.isPresent());
        assertTrue(accounts.findById(verified.get().accountId()).isEmpty());
    }

    @Test
    @DisplayName("A session records the client it was opened from, and when it was last seen")
    void sessionRemembersItsClient() {
        JwtService.Issued issued = signIn("Firefox on Linux");

        accountSessions.touchLastSeen(issued.jti());

        AccountSession session = accountSessions.find(issued.jti()).orElseThrow();
        assertEquals("Firefox on Linux", session.userAgent());
        assertTrue(session.lastSeenAt().isAfter(Instant.now().minus(Duration.ofMinutes(1))));
    }
}
