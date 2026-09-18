/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetTokensRepositoryTest extends RepositoryTestBase {
    private Account account;

    @BeforeEach
    void freshAccount() throws SQLException {
        clear("password_reset_token", "account_identity", "account");
        account = accountService.register("reset@example.invalid", "hash");
    }

    @Test
    @DisplayName("An issued token names the account it was issued for")
    void issueAndConsume() {
        PasswordResetTokenRepository.Issued issued =
                passwordResetTokens.issue(account.id(), Instant.now().plus(Duration.ofHours(1)));

        assertEquals(64, issued.token().length());
        assertEquals(account.id(), passwordResetTokens.consume(issued.token()).orElseThrow());
    }

    @Test
    @DisplayName("A token works once and is spent afterwards")
    void tokenIsSingleUse() {
        PasswordResetTokenRepository.Issued issued =
                passwordResetTokens.issue(account.id(), Instant.now().plus(Duration.ofHours(1)));

        assertTrue(passwordResetTokens.consume(issued.token()).isPresent());
        assertTrue(passwordResetTokens.consume(issued.token()).isEmpty());
    }

    @Test
    @DisplayName("A token past its expiry is refused and stays unspent")
    void expiredTokenIsRefused() {
        PasswordResetTokenRepository.Issued issued =
                passwordResetTokens.issue(account.id(), Instant.now().minus(Duration.ofSeconds(1)));

        assertTrue(passwordResetTokens.consume(issued.token()).isEmpty());
    }

    @Test
    @DisplayName("A token nobody issued is refused")
    void unknownTokenIsRefused() {
        assertTrue(passwordResetTokens.consume("0".repeat(64)).isEmpty());
    }

    @Test
    @DisplayName("Asking for a second token takes the first one away")
    void reissueReplacesTheOutstandingToken() {
        PasswordResetTokenRepository.Issued first =
                passwordResetTokens.issue(account.id(), Instant.now().plus(Duration.ofHours(1)));
        PasswordResetTokenRepository.Issued second =
                passwordResetTokens.issue(account.id(), Instant.now().plus(Duration.ofHours(1)));

        assertNotEquals(first.token(), second.token());
        assertTrue(passwordResetTokens.consume(first.token()).isEmpty());
        assertEquals(account.id(), passwordResetTokens.consume(second.token()).orElseThrow());
    }

    @Test
    @DisplayName("Pruning drops the tokens that can no longer be redeemed")
    void pruneRemovesExpiredOnly() {
        Account other = accountService.register("other-reset@example.invalid", "hash");
        PasswordResetTokenRepository.Issued live =
                passwordResetTokens.issue(account.id(), Instant.now().plus(Duration.ofHours(1)));
        passwordResetTokens.issue(other.id(), Instant.now().minus(Duration.ofHours(1)));

        assertEquals(1, passwordResetTokens.prune());

        assertEquals(account.id(), passwordResetTokens.consume(live.token()).orElseThrow());
    }

    @Test
    @DisplayName("Deleting an account takes its outstanding reset token with it")
    void deletingAccountRemovesToken() {
        PasswordResetTokenRepository.Issued issued =
                passwordResetTokens.issue(account.id(), Instant.now().plus(Duration.ofHours(1)));

        accounts.delete(account.id());

        assertTrue(passwordResetTokens.consume(issued.token()).isEmpty());
    }
}
