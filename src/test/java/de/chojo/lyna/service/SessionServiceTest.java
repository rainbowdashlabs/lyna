/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.configuration.elements.Auth;
import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.entity.AccountSession;
import de.chojo.lyna.feature.account.repository.AccountSessionRepository;
import de.chojo.lyna.feature.account.service.SessionService;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The signing in, staying signed in and signing out that a browser session is made of, across the
 * token service and the session rows that say which tokens still count.
 */
class SessionServiceTest extends RepositoryTestBase {
    private Account account;

    @BeforeEach
    void freshAccount() throws SQLException {
        clear("account_session", "account_identity", "account");
        account = accountService.register("session@example.invalid", "hash");
    }

    private static JwtService jwt(String secret, int renewAfterHours) {
        Auth config = Mockito.mock(Auth.class);
        Mockito.when(config.jwtSecret()).thenReturn(secret);
        Mockito.when(config.sessionDays()).thenReturn(30);
        Mockito.when(config.renewAfterHours()).thenReturn(renewAfterHours);
        return new JwtService(config);
    }

    private static SessionService sessions(int renewAfterHours) {
        return new SessionService(jwt("service-test-secret", renewAfterHours), accountSessions);
    }

    @Test
    @DisplayName("Signing in records a session that is listed and recognised")
    void openRecordsTheSession() {
        SessionService service = sessions(24);

        JwtService.Issued issued = service.open(account.id(), "Firefox");

        AccountSession row = accountSessions.find(issued.jti()).orElseThrow();
        assertEquals(account.id(), row.accountId());
        assertEquals("Firefox", row.userAgent());
        var recognised = service.recognise(issued.token()).orElseThrow();
        assertEquals(account.id(), recognised.session().accountId());
        assertTrue(recognised.renewal().isEmpty(), "a token younger than the renewal age is kept");
        assertNotNull(accountSessions.find(issued.jti()).orElseThrow().lastSeenAt());
    }

    @Test
    @DisplayName("A token past the renewal age comes back with a successor for the same session")
    void oldTokenIsRenewed() throws SQLException {
        SessionService service = sessions(0);
        JwtService.Issued issued = service.open(account.id(), "Firefox");
        execute("UPDATE %s.account_session SET expires_at = now() + INTERVAL '1 hour'");

        JwtService.Issued renewed =
                service.recognise(issued.token()).orElseThrow().renewal().orElseThrow();

        assertEquals(issued.jti(), renewed.jti());
        assertTrue(accountSessions
                .find(issued.jti())
                .orElseThrow()
                .expiresAt()
                .isAfter(Instant.now().plus(Duration.ofDays(29))));
        assertEquals(1, accountSessions.activeForAccount(account.id()).size(), "renewal adds no row");
        assertTrue(
                service.recognise(issued.token()).isPresent(),
                "the predecessor stays valid for a tab still holding it");
    }

    @Test
    @DisplayName("Signing out ends every token of that session and no other session")
    void endIsPerSession() {
        SessionService service = sessions(24);
        JwtService.Issued laptop = service.open(account.id(), "Firefox");
        JwtService.Issued phone = service.open(account.id(), "Safari on iOS");

        assertTrue(service.end(laptop.jti()));

        assertTrue(service.recognise(laptop.token()).isEmpty());
        assertTrue(service.recognise(phone.token()).isPresent());
        assertFalse(service.end(laptop.jti()), "an ended session has nothing left to end");
    }

    @Test
    @DisplayName("Nothing, garbage and a token from another instance are not sessions")
    void unknownTokensAreRefused() {
        SessionService service = sessions(24);
        JwtService.Issued foreign =
                new SessionService(jwt("a-different-secret", 24), accountSessions).open(account.id(), null);

        assertTrue(service.recognise(null).isEmpty());
        assertTrue(service.recognise(" ").isEmpty());
        assertTrue(service.recognise("not.a.token").isEmpty());
        assertTrue(service.recognise(foreign.token()).isEmpty());
    }

    @Test
    @DisplayName("A signed token whose session row is gone is refused")
    void tokenWithoutRowIsRefused() {
        JwtService.Issued unrecorded = jwt("service-test-secret", 24).issue(account.id());

        assertTrue(sessions(24).recognise(unrecorded.token()).isEmpty());
        assertTrue(sessions(24).adopt(unrecorded.token()).isEmpty());
    }

    @Test
    @DisplayName("Adopting a stored token renews the same session, whatever the token's age")
    void adoptRenewsTheSession() throws SQLException {
        SessionService service = sessions(24);
        JwtService.Issued issued = service.open(account.id(), "Firefox");
        execute("UPDATE %s.account_session SET expires_at = now() + INTERVAL '1 hour'");

        JwtService.Issued adopted = service.adopt(issued.token()).orElseThrow();

        assertEquals(issued.jti(), adopted.jti());
        assertTrue(accountSessions
                .find(issued.jti())
                .orElseThrow()
                .expiresAt()
                .isAfter(Instant.now().plus(Duration.ofDays(29))));
    }

    @Test
    @DisplayName("A token of an ended session, or none at all, is not adopted")
    void adoptRefusesEndedSessions() {
        SessionService service = sessions(24);
        JwtService.Issued issued = service.open(account.id(), "Firefox");
        service.end(issued.jti());

        assertTrue(service.adopt(issued.token()).isEmpty());
        assertTrue(service.adopt(null).isEmpty());
        assertTrue(service.adopt("").isEmpty());
    }

    @Test
    @DisplayName("A session that is ended between being recognised and being renewed is not handed back")
    void renewalOfAnEndedSessionFails() {
        AccountSessionRepository racing = Mockito.mock(AccountSessionRepository.class);
        Mockito.when(racing.touchIfLive(Mockito.anyString())).thenReturn(true);
        Mockito.when(racing.renew(Mockito.anyString(), Mockito.any())).thenReturn(false);
        SessionService service = new SessionService(jwt("service-test-secret", 0), racing);
        JwtService.Issued issued = service.open(account.id(), "Firefox");

        assertTrue(service.recognise(issued.token()).isEmpty());
        assertTrue(service.adopt(issued.token()).isEmpty());
    }

    @Test
    @DisplayName("Deleting the account takes its sessions, so its tokens stop counting")
    void deletingTheAccountEndsItsSessions() {
        SessionService service = sessions(24);
        JwtService.Issued issued = service.open(account.id(), "Firefox");

        accountSessions.deleteAllForAccount(account.id());
        accounts.delete(account.id());

        assertTrue(service.recognise(issued.token()).isEmpty());
    }
}
