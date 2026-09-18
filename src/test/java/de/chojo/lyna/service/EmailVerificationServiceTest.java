/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirming that somebody can read the address they gave.
 *
 * <p>Typing an address proves nothing about being able to read it, so nothing an address is good for
 * happens until the link sent to it is followed.
 *
 * <p>An account holds several. Confirming a second one <em>adds</em> it rather than replacing the
 * first - which is what lets somebody who paid from one address and signed up with another be one
 * person.
 */
class EmailVerificationServiceTest extends RepositoryTestBase {
    private Account account;

    @BeforeEach
    void freshAccount() throws SQLException {
        clear("email_verification_token", "account_email", "account_identity", "account");
        account = accounts.create("first@example.invalid", "hash");
    }

    private Optional<String> issueFor(String email) {
        return Optional.of(emailVerificationTokens
                .issue(account.id(), email, Instant.now().plus(Duration.ofDays(1)))
                .token());
    }

    private boolean confirm(String token) {
        var confirmed = emailVerificationTokens.consume(token);
        confirmed.ifPresent(c -> accounts.confirmEmail(c.accountId(), c.email()));
        return confirmed.isPresent();
    }

    private Account reload() {
        return accounts.findById(account.id()).orElseThrow();
    }

    @Test
    @DisplayName("A fresh account has an address nobody has confirmed")
    void freshAccountIsUnverified() {
        assertFalse(reload().emailVerified());
        assertEquals("first@example.invalid", reload().email());
    }

    @Test
    @DisplayName("Following the link confirms the address")
    void confirmingMarksItVerified() {
        String token = issueFor("first@example.invalid").orElseThrow();

        assertTrue(confirm(token));

        assertTrue(reload().emailVerified());
    }

    @Test
    @DisplayName("Confirming a second address adds it, and the account keeps the one it is written to")
    void confirmingASecondAddressAddsIt() {
        confirm(issueFor("first@example.invalid").orElseThrow());

        confirm(issueFor("second@example.invalid").orElseThrow());

        assertEquals("first@example.invalid", reload().email(), "the address it is written to is unchanged");
        assertEquals(2, accountEmails.of(account.id()).size());
        assertTrue(
                accountEmails.byAddress("second@example.invalid").orElseThrow().verified());
    }

    @Test
    @DisplayName("An account with no address yet is written to the first one it confirms")
    void theFirstConfirmedAddressBecomesThePrimary() {
        Account bare = accounts.create(null, "hash");

        accounts.confirmEmail(bare.id(), "only@example.invalid");

        assertEquals(
                "only@example.invalid",
                accounts.findById(bare.id()).orElseThrow().email());
        assertTrue(accountEmails.primary(bare.id()).orElseThrow().verified());
    }

    @Test
    @DisplayName("An address another account holds cannot be taken by confirming it")
    void anAddressBelongsToOneAccount() {
        Account other = accounts.create("taken@example.invalid", "hash");
        assertTrue(other.id() != account.id());

        assertThrows(IllegalStateException.class, () -> accounts.confirmEmail(account.id(), "taken@example.invalid"));
    }

    @Test
    @DisplayName("The link works once")
    void tokenIsSingleUse() {
        String token = issueFor("second@example.invalid").orElseThrow();

        assertTrue(confirm(token));
        assertFalse(confirm(token));
    }

    @Test
    @DisplayName("A link past its day is refused, and the address is left alone")
    void expiredTokenIsRefused() {
        var issued = emailVerificationTokens.issue(
                account.id(), "second@example.invalid", Instant.now().minus(Duration.ofMinutes(1)));

        assertFalse(confirm(issued.token()));

        assertEquals("first@example.invalid", reload().email());
        assertFalse(reload().emailVerified());
    }

    @Test
    @DisplayName("A token nobody issued is refused")
    void unknownTokenIsRefused() {
        assertFalse(confirm("0".repeat(64)));
    }

    @Test
    @DisplayName("Asking again replaces the outstanding link, so only the newest mail works")
    void reissueReplacesTheOutstandingToken() {
        String first = issueFor("second@example.invalid").orElseThrow();
        String second = issueFor("second@example.invalid").orElseThrow();

        assertNotEquals(first, second);
        assertFalse(confirm(first));
        assertTrue(confirm(second));
    }

    @Test
    @DisplayName("The address a link is outstanding for can be read back, for showing it on the page")
    void pendingAddressIsReadable() {
        assertTrue(emailVerificationTokens.pendingEmail(account.id()).isEmpty());

        issueFor("second@example.invalid");

        assertEquals(
                "second@example.invalid",
                emailVerificationTokens.pendingEmail(account.id()).orElseThrow());
    }

    @Test
    @DisplayName("An expired link is not an address still waiting to be confirmed")
    void expiredTokenIsNotPending() {
        emailVerificationTokens.issue(
                account.id(), "second@example.invalid", Instant.now().minus(Duration.ofMinutes(1)));

        assertTrue(emailVerificationTokens.pendingEmail(account.id()).isEmpty());
    }

    @Test
    @DisplayName("Pruning drops the links that can no longer be followed")
    void pruneRemovesExpiredOnly() {
        Account other = accounts.create("other@example.invalid", "hash");
        emailVerificationTokens.issue(
                other.id(), "other@example.invalid", Instant.now().minus(Duration.ofHours(1)));
        String live = issueFor("second@example.invalid").orElseThrow();

        assertEquals(1, emailVerificationTokens.prune());

        assertTrue(confirm(live));
    }

    @Test
    @DisplayName("Deleting an account takes its outstanding link with it")
    void deletingAccountRemovesToken() {
        String token = issueFor("second@example.invalid").orElseThrow();

        accounts.delete(account.id());

        assertTrue(emailVerificationTokens.consume(token).isEmpty());
    }

    @Test
    @DisplayName("Confirming one account's address says nothing about another's")
    void confirmationIsPerAccount() {
        Account other = accounts.create("other@example.invalid", "hash");
        String token = issueFor("first@example.invalid").orElseThrow();
        confirm(token);

        assertTrue(reload().emailVerified());
        assertFalse(accounts.findById(other.id()).orElseThrow().emailVerified());
    }
}
