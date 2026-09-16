package de.chojo.lyna.service;

import de.chojo.lyna.data.access.EmailVerificationTokens;
import de.chojo.lyna.data.dao.account.Account;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirming that somebody can read the address they gave.
 *
 * <p>The account keeps the address it had until the link is followed, which is the whole point:
 * typing an address proves nothing about being able to read it.
 */
class EmailVerificationServiceTest extends RepositoryTestBase {
    private Account account;

    @BeforeEach
    void freshAccount() throws SQLException {
        clear("email_verification_token", "account_identity", "account");
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
    @DisplayName("A change takes effect only once the new address is confirmed")
    void changeTakesEffectOnConfirmation() {
        String token = issueFor("second@example.invalid").orElseThrow();

        assertEquals("first@example.invalid", reload().email(), "the old address stands until confirmed");

        confirm(token);

        assertEquals("second@example.invalid", reload().email());
        assertTrue(reload().emailVerified());
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

        assertEquals("second@example.invalid", emailVerificationTokens.pendingEmail(account.id()).orElseThrow());
    }

    @Test
    @DisplayName("An expired link is not an address still waiting to be confirmed")
    void expiredTokenIsNotPending() {
        emailVerificationTokens.issue(account.id(), "second@example.invalid",
                Instant.now().minus(Duration.ofMinutes(1)));

        assertTrue(emailVerificationTokens.pendingEmail(account.id()).isEmpty());
    }

    @Test
    @DisplayName("Pruning drops the links that can no longer be followed")
    void pruneRemovesExpiredOnly() {
        Account other = accounts.create("other@example.invalid", "hash");
        emailVerificationTokens.issue(other.id(), "other@example.invalid",
                Instant.now().minus(Duration.ofHours(1)));
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
