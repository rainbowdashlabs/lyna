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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Making an account, and the reads the pages ask of it.
 */
class AccountRegistrationServiceTest extends RepositoryTestBase {

    @BeforeEach
    void clean() throws SQLException {
        clear("account_email", "account_identity", "account");
    }

    @Test
    @DisplayName("An account can be made with no address at all")
    void anAddresslessAccount() {
        Account created = accountService.register(null, "hash");

        assertTrue(accountService.findById(created.id()).isPresent());
        assertTrue(accountEmails.of(created.id()).isEmpty(), "nothing to write to yet");
    }

    @Test
    @DisplayName("A blank address is the same as none")
    void blankIsNone() {
        Account created = accountService.register("   ", "hash");
        assertTrue(accountEmails.of(created.id()).isEmpty());
    }

    @Test
    @DisplayName("An address another account is written to is refused")
    void anExclusiveAddressIsRefused() {
        accountService.register("taken@example.invalid", "hash");

        assertThrows(IllegalStateException.class, () -> accountService.register("taken@example.invalid", "other"));
    }

    @Test
    @DisplayName("An address only somebody else claimed is not refused, because claiming is not owning")
    void aClaimedAddressIsNotRefused() {
        Account first = accountService.register("first@example.invalid", "hash");
        accountEmails.add(first.id(), "contested@example.invalid");

        Account second = accountService.register("contested@example.invalid", "hash");
        assertEquals(
                second.id(),
                accountEmails
                        .byAddress("contested@example.invalid")
                        .orElseThrow()
                        .accountId(),
                "the one written to it is the one it names");
    }

    @Test
    @DisplayName("An account is found by address, by Discord id and by the name it shows")
    void theReadsThePagesUse() {
        Account created = accountService.register("found@example.invalid", "hash");
        accountLinks.link(
                created.id(), 7001L, de.chojo.lyna.feature.account.entity.AccountIdentity.Verification.OAUTH, "ada");

        assertEquals(
                created.id(),
                accountService
                        .findByEmail("found@example.invalid")
                        .orElseThrow()
                        .id());
        assertEquals(
                created.id(),
                accountService.findByDiscordId(7001L).orElseThrow().id());
        assertEquals(
                created.id(), accountService.findByUsername("ada").orElseThrow().id());
        assertTrue(accountService.findByDiscordId(9999L).isEmpty());
    }

    @Test
    @DisplayName("Signing in, changing a password and choosing an appearance are all recorded")
    void theWritesThePagesUse() {
        Account created = accountService.register("writes@example.invalid", "hash");

        accountService.touchLastLogin(created.id());
        assertTrue(accountService.findById(created.id()).orElseThrow().lastLoginAt() != null);

        accountService.setPasswordHash(created.id(), "newhash");
        assertEquals(
                "newhash", accountService.findById(created.id()).orElseThrow().passwordHash());

        accountService.setAppearance(created.id(), "transistor", "DARK");
        assertEquals(
                "transistor",
                accountService.findById(created.id()).orElseThrow().theme());

        accountService.delete(created.id());
        assertTrue(accountService.findById(created.id()).isEmpty());
    }
}
