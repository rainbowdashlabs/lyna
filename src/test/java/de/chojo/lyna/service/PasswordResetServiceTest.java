/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.data.access.PasswordResetTokens;
import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The forgotten-password journey: ask for a link, follow it once, and sign in with what was set.
 */
class PasswordResetServiceTest extends RepositoryTestBase {
    private static final PasswordHasher HASHER = new PasswordHasher();

    private Account account;

    @BeforeEach
    void freshAccount() throws SQLException {
        clear("password_reset_token", "account_session", "account_identity", "account");
        account = accounts.create("forgetful@example.invalid", HASHER.hash("forgotten"));
    }

    /**
     * @return the token a reset mail would carry, for an address that has an account
     */
    private Optional<String> requestReset(String email) {
        return accounts.findByEmail(email)
                .map(found ->
                        passwordResetTokens.issue(found.id(), Instant.now().plus(Duration.ofHours(1))))
                .map(PasswordResetTokens.Issued::token);
    }

    /**
     * @return whether the token was accepted and the new password stored
     */
    private boolean confirmReset(String token, String newPassword) {
        Optional<Integer> accountId = passwordResetTokens.consume(token);
        accountId.ifPresent(id -> accounts.setPasswordHash(id, HASHER.hash(newPassword)));
        return accountId.isPresent();
    }

    private boolean signsIn(String password) {
        return accounts.findById(account.id())
                .map(found -> HASHER.verify(password, found.passwordHash()))
                .orElse(false);
    }

    @Test
    @DisplayName("Following the link sets the new password and retires the forgotten one")
    void resetReplacesThePassword() {
        String token = requestReset("forgetful@example.invalid").orElseThrow();

        assertTrue(confirmReset(token, "remembered"));

        assertTrue(signsIn("remembered"));
        assertFalse(signsIn("forgotten"));
    }

    @Test
    @DisplayName("The link works once; a second visit changes nothing")
    void theLinkIsSingleUse() {
        String token = requestReset("forgetful@example.invalid").orElseThrow();
        assertTrue(confirmReset(token, "first reset"));

        assertFalse(confirmReset(token, "second reset"));

        assertTrue(signsIn("first reset"));
    }

    @Test
    @DisplayName("Asking twice invalidates the first link, so only the newest mail works")
    void onlyTheNewestLinkWorks() {
        String first = requestReset("forgetful@example.invalid").orElseThrow();
        String second = requestReset("forgetful@example.invalid").orElseThrow();

        assertFalse(confirmReset(first, "from the old mail"));
        assertTrue(confirmReset(second, "from the new mail"));

        assertTrue(signsIn("from the new mail"));
    }

    @Test
    @DisplayName("An expired link is refused and leaves the password alone")
    void expiredLinkIsRefused() {
        PasswordResetTokens.Issued issued =
                passwordResetTokens.issue(account.id(), Instant.now().minus(Duration.ofMinutes(1)));

        assertFalse(confirmReset(issued.token(), "too late"));

        assertTrue(signsIn("forgotten"));
    }

    @Test
    @DisplayName("An address nobody registered produces no token to leak that fact")
    void unknownAddressProducesNoToken() {
        assertTrue(requestReset("stranger@example.invalid").isEmpty());
    }

    @Test
    @DisplayName("An account that never had a password can be given one through a reset")
    void resetGivesAPasswordToAnOAuthAccount() throws SQLException {
        Account oauthOnly = accounts.create("discord-only@example.invalid", null);
        assertFalse(oauthOnly.hasPassword());

        String token = requestReset("discord-only@example.invalid").orElseThrow();
        assertTrue(confirmReset(token, "now has one"));

        Account found = accounts.findById(oauthOnly.id()).orElseThrow();
        assertTrue(found.hasPassword());
        assertTrue(HASHER.verify("now has one", found.passwordHash()));
    }
}
