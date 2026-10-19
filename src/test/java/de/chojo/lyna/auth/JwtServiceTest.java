/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.auth;

import de.chojo.lyna.configuration.elements.Auth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private JwtService service(int sessionDays, int renewAfterHours) {
        Auth config = Mockito.mock(Auth.class);
        Mockito.when(config.jwtSecret()).thenReturn("test-secret-please-rotate");
        Mockito.when(config.sessionDays()).thenReturn(sessionDays);
        Mockito.when(config.renewAfterHours()).thenReturn(renewAfterHours);
        return new JwtService(config);
    }

    @Test
    void issueAndVerify() {
        JwtService svc = service(30, 24);
        JwtService.Issued issued = svc.issue(42);
        assertNotNull(issued.jti());

        Optional<JwtService.Verified> verified = svc.verify(issued.token());
        assertTrue(verified.isPresent());
        assertEquals(42, verified.get().accountId());
        assertEquals(issued.jti(), verified.get().jti());
        assertNotNull(verified.get().issuedAt());
    }

    @Test
    @DisplayName("A session lasts the configured number of days")
    void lastsTheSessionLength() {
        JwtService.Issued issued = service(30, 24).issue(1);

        Duration left = Duration.between(Instant.now(), issued.expiresAt());
        assertTrue(left.compareTo(Duration.ofDays(30).minusMinutes(1)) > 0);
        assertTrue(left.compareTo(Duration.ofDays(30)) <= 0);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService svc = service(-1, 24);
        assertFalse(svc.verify(svc.issue(7).token()).isPresent());
    }

    @Test
    void rejectsTamperedSignature() {
        JwtService svc = service(30, 24);
        String token = svc.issue(1).token();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertFalse(svc.verify(tampered).isPresent());
    }

    @Test
    @DisplayName("A renewed token belongs to the same session and runs a whole session length from now")
    void renewKeepsTheSession() {
        JwtService svc = service(30, 24);
        JwtService.Verified old = new JwtService.Verified(
                5,
                "session-id",
                Instant.now().minus(Duration.ofDays(2)),
                Instant.now().plus(Duration.ofDays(28)));

        JwtService.Issued renewed = svc.renew(old);

        assertEquals("session-id", renewed.jti());
        assertTrue(renewed.expiresAt().isAfter(old.expiresAt()));
        JwtService.Verified verified = svc.verify(renewed.token()).orElseThrow();
        assertEquals(5, verified.accountId());
        assertEquals("session-id", verified.jti());
        assertNotEquals(old.issuedAt(), verified.issuedAt());
    }

    @Test
    @DisplayName("A token is renewed once it is older than the renewal age, and not before")
    void dueForRenewal() {
        JwtService svc = service(30, 24);
        Instant expires = Instant.now().plus(Duration.ofDays(29));

        assertFalse(
                svc.dueForRenewal(new JwtService.Verified(1, "j", Instant.now().minus(Duration.ofHours(23)), expires)));
        assertTrue(
                svc.dueForRenewal(new JwtService.Verified(1, "j", Instant.now().minus(Duration.ofHours(25)), expires)));
        assertTrue(svc.dueForRenewal(new JwtService.Verified(1, "j", null, expires)), "a token without iat is renewed");
    }

    @Test
    @DisplayName("Without a secret nothing can be signed, and the service says so")
    void refusesWithoutSecret() {
        Auth config = Mockito.mock(Auth.class);
        Mockito.when(config.jwtSecret()).thenReturn(" ");
        assertThrows(IllegalStateException.class, () -> new JwtService(config));
    }
}
