/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.account.entity.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The addresses an account holds.
 *
 * <p>Two rules carry the weight: an account may hold several, and an address belongs to one account.
 * The second is what stops somebody collecting a licence bought by another.
 */
class AccountEmailsRepositoryTest extends RepositoryTestBase {
    private Account account;

    @BeforeEach
    void freshAccount() throws SQLException {
        clear("account_email", "account_identity", "account");
        account = accounts.create("first@example.invalid", "hash");
    }

    @Test
    @DisplayName("The address an account is created with is its first, and the one it is written to")
    void theFirstAddressIsThePrimary() {
        assertEquals(1, accountEmails.of(account.id()).size());
        assertEquals(
                "first@example.invalid",
                accountEmails.primary(account.id()).orElseThrow().email());
    }

    @Test
    @DisplayName("An address arrives unverified, because typing it proves nothing")
    void addedAddressesStartUnverified() {
        assertFalse(accountEmails.primary(account.id()).orElseThrow().verified());

        accountEmails.add(account.id(), "second@example.invalid");

        assertFalse(accountEmails
                .heldBy(account.id(), "second@example.invalid")
                .orElseThrow()
                .verified());
    }

    @Test
    @DisplayName("An account holds as many addresses as it proves")
    void severalAddresses() {
        accountEmails.add(account.id(), "second@example.invalid");
        accountEmails.add(account.id(), "third@example.invalid");

        assertEquals(3, accountEmails.of(account.id()).size());
    }

    @Test
    @DisplayName("An address an account is written to belongs to it, whatever case it is typed in")
    void anAddressBelongsToOneAccount() {
        Account other = accounts.create("other@example.invalid", "hash");

        assertThrows(IllegalStateException.class, () -> accountEmails.add(other.id(), "FIRST@example.invalid"));
        assertEquals(
                account.id(),
                accountEmails.byAddress("first@example.invalid").orElseThrow().accountId());
    }

    /**
     * Reserving an address on the strength of somebody typing it would let anyone take one they do
     * not own away from whoever does. So two accounts may both claim it, and proving it settles who
     * has it.
     */
    @Test
    @DisplayName("Two accounts may both claim an address nobody has proved")
    void claimsDoNotReserve() {
        Account other = accounts.create("other@example.invalid", "hash");

        accountEmails.add(account.id(), "contested@example.invalid");
        accountEmails.add(other.id(), "contested@example.invalid");

        assertTrue(accountEmails.byAddress("contested@example.invalid").isEmpty(), "it names nobody yet");
    }

    @Test
    @DisplayName("Proving a contested address settles it, and the other claim is refused")
    void provingSettlesAContestedAddress() {
        Account other = accounts.create("other@example.invalid", "hash");
        accountEmails.add(account.id(), "contested@example.invalid");
        accountEmails.add(other.id(), "contested@example.invalid");

        assertTrue(accountEmails.verify(account.id(), "contested@example.invalid"));

        assertEquals(
                account.id(),
                accountEmails
                        .byAddress("contested@example.invalid")
                        .orElseThrow()
                        .accountId());
        assertThrows(IllegalStateException.class, () -> accountEmails.verify(other.id(), "contested@example.invalid"));
    }

    @Test
    @DisplayName("Adding an address the account already holds changes nothing")
    void addingTwiceIsHarmless() {
        accountEmails.add(account.id(), "first@example.invalid");

        assertEquals(1, accountEmails.of(account.id()).size());
    }

    @Test
    @DisplayName("Only a verified address can become the one the account is written to")
    void onlyAVerifiedAddressCanBePrimary() {
        accountEmails.add(account.id(), "second@example.invalid");

        assertFalse(accountEmails.makePrimary(account.id(), "second@example.invalid"));

        accountEmails.verify(account.id(), "second@example.invalid");

        assertTrue(accountEmails.makePrimary(account.id(), "second@example.invalid"));
        assertEquals(
                "second@example.invalid",
                accountEmails.primary(account.id()).orElseThrow().email());
    }

    @Test
    @DisplayName("An account is written to one address at a time")
    void onlyOnePrimary() {
        accountEmails.add(account.id(), "second@example.invalid");
        accountEmails.verify(account.id(), "second@example.invalid");
        accountEmails.makePrimary(account.id(), "second@example.invalid");

        assertEquals(
                1,
                accountEmails.of(account.id()).stream().filter(e -> e.primary()).count());
    }

    @Test
    @DisplayName("The address the account is written to cannot be given up")
    void thePrimaryCannotBeRemoved() {
        assertFalse(accountEmails.remove(account.id(), "first@example.invalid"));
        assertEquals(1, accountEmails.of(account.id()).size());
    }

    @Test
    @DisplayName("Any other address can be given up")
    void otherAddressesCanBeRemoved() {
        accountEmails.add(account.id(), "second@example.invalid");

        assertTrue(accountEmails.remove(account.id(), "second@example.invalid"));

        assertEquals(1, accountEmails.of(account.id()).size());
        assertTrue(accountEmails.heldBy(account.id(), "second@example.invalid").isEmpty());
    }

    @Test
    @DisplayName("One account cannot give up another's address")
    void cannotRemoveSomebodyElsesAddress() {
        Account other = accounts.create("other@example.invalid", "hash");
        accountEmails.add(other.id(), "theirs@example.invalid");

        assertFalse(accountEmails.remove(account.id(), "theirs@example.invalid"));
        assertTrue(accountEmails.heldBy(other.id(), "theirs@example.invalid").isPresent());
    }

    @Test
    @DisplayName("Verifying twice keeps the moment it was first proved")
    void verifyingIsIdempotent() {
        accountEmails.verify(account.id(), "first@example.invalid");
        var first = accountEmails.primary(account.id()).orElseThrow().verifiedAt();

        accountEmails.verify(account.id(), "first@example.invalid");

        assertEquals(first, accountEmails.primary(account.id()).orElseThrow().verifiedAt());
    }

    @Test
    @DisplayName("An account cannot verify an address it does not hold")
    void cannotVerifyAnAddressItDoesNotHold() {
        assertFalse(accountEmails.verify(account.id(), "never-added@example.invalid"));
    }

    @Test
    @DisplayName("Deleting an account takes its addresses with it, freeing them")
    void deletingAnAccountFreesItsAddresses() {
        accounts.delete(account.id());

        assertTrue(accountEmails.byAddress("first@example.invalid").isEmpty());
    }
}
