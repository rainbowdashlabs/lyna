/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signing up with an address and a password, signing in with it again, and changing it - the part
 * of the account that a password is.
 */
class CredentialsServiceTest extends RepositoryTestBase {
    private static final PasswordHasher HASHER = new PasswordHasher();

    @BeforeEach
    void clearAccounts() throws SQLException {
        clear("account_identity", "account");
    }

    /**
     * @return whether the stored hash accepts the password offered, which is what a login decides on
     */
    private boolean signsIn(String email, String password) {
        return accounts.findByEmail(email)
                .filter(Account::hasPassword)
                .map(account -> HASHER.verify(password, account.passwordHash()))
                .orElse(false);
    }

    @Test
    @DisplayName("A password set at signup signs the account in again, a wrong one does not")
    void signupThenSignIn() {
        accounts.create("member@example.invalid", HASHER.hash("correct horse"));

        assertTrue(signsIn("member@example.invalid", "correct horse"));
        assertFalse(signsIn("member@example.invalid", "correct horses"));
        assertFalse(signsIn("member@example.invalid", ""));
    }

    @Test
    @DisplayName("The stored password is a hash, not the password")
    void passwordIsNotStoredInTheClear() {
        Account created = accounts.create("hashed@example.invalid", HASHER.hash("plaintext"));

        assertFalse(created.passwordHash().contains("plaintext"));
        assertTrue(created.passwordHash().startsWith("$2"));
    }

    @Test
    @DisplayName("Two accounts on the same password do not share a hash")
    void hashesAreSalted() {
        Account first = accounts.create("first@example.invalid", HASHER.hash("same password"));
        Account second = accounts.create("second@example.invalid", HASHER.hash("same password"));

        assertFalse(first.passwordHash().equals(second.passwordHash()));
        assertTrue(signsIn("first@example.invalid", "same password"));
        assertTrue(signsIn("second@example.invalid", "same password"));
    }

    @Test
    @DisplayName("Changing the password retires the old one")
    void changingThePasswordRetiresTheOldOne() {
        Account created = accounts.create("rotate@example.invalid", HASHER.hash("first"));

        accounts.setPasswordHash(created.id(), HASHER.hash("second"));

        assertFalse(signsIn("rotate@example.invalid", "first"));
        assertTrue(signsIn("rotate@example.invalid", "second"));
    }

    @Test
    @DisplayName("An account that signed up through Discord cannot be signed into with a password")
    void accountWithoutPasswordCannotSignIn() {
        accounts.create("oauth-only@example.invalid", null);

        assertFalse(signsIn("oauth-only@example.invalid", ""));
        assertFalse(signsIn("oauth-only@example.invalid", "anything"));
    }

    @Test
    @DisplayName("Adding a password later lets that account sign in with one")
    void passwordCanBeAddedLater() {
        Account created = accounts.create("adds-password@example.invalid", null);
        assertFalse(signsIn("adds-password@example.invalid", "later"));

        accounts.setPasswordHash(created.id(), HASHER.hash("later"));

        assertTrue(signsIn("adds-password@example.invalid", "later"));
    }
}
