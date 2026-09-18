/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.data.dao.account.AccountLicense;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountLicensesRepositoryTest extends RepositoryTestBase {
    private static final long GUILD_A = 1001L;
    private static final long GUILD_B = 1002L;
    private static final long OWNER_DISCORD = 500L;
    private static final long SHAREE_DISCORD = 600L;

    private int chattyLicense;
    private int otherGuildLicense;
    private int owner;
    private int sharee;

    @BeforeEach
    void seedLicenses() throws SQLException {
        clear(
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "license_settings",
                "product",
                "account_identity",
                "account");
        owner = de.chojo.lyna.data.access.Accounts.accountIdForDiscord(OWNER_DISCORD);
        sharee = de.chojo.lyna.data.access.Accounts.accountIdForDiscord(SHAREE_DISCORD);

        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO %s.license_settings (guild_id, shares) VALUES (%d, 3)".formatted(schemaName, GUILD_A));
            int chatty = insert(
                    statement,
                    "INSERT INTO product (guild_id, name, url, role) VALUES (%d, 'Chatty', 'https://example.invalid/chatty', 1) RETURNING id"
                            .formatted(GUILD_A));
            int elsewhere = insert(
                    statement,
                    "INSERT INTO product (guild_id, name, role) VALUES (%d, 'Elsewhere', 2) RETURNING id"
                            .formatted(GUILD_B));

            chattyLicense = insert(statement, """
                    INSERT INTO license (product_id, user_identifier, key)
                    VALUES (%d, 'buyer@example.invalid', 'KEY-CHATTY') RETURNING id
                    """.formatted(chatty));
            otherGuildLicense = insert(statement, """
                    INSERT INTO license (product_id, user_identifier, key)
                    VALUES (%d, 'buyer@example.invalid', 'KEY-ELSEWHERE') RETURNING id
                    """.formatted(elsewhere));

            statement.execute("INSERT INTO %s.user_license (account_id, license_id) VALUES (%d, %d), (%d, %d)"
                    .formatted(schemaName, owner, chattyLicense, owner, otherGuildLicense));
            statement.execute(
                    "INSERT INTO %s.license_access (license_id, release_type) VALUES (%d, 'STABLE'), (%d, 'DEV')"
                            .formatted(schemaName, chattyLicense, chattyLicense));
        }
    }

    private static int insert(Statement statement, String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    @DisplayName("An owner sees every license they hold, whichever guild issued it")
    void ownedIsCrossGuild() {
        List<AccountLicense> owned = accountLicenses.owned(owner);

        assertEquals(
                List.of("Chatty", "Elsewhere"),
                owned.stream().map(AccountLicense::productName).toList());
        assertEquals(
                List.of(GUILD_A, GUILD_B),
                owned.stream().map(AccountLicense::guildId).toList());
        assertTrue(owned.stream().allMatch(license -> license.role() == AccountLicense.Role.OWNER));
    }

    @Test
    @DisplayName("A license carries its release types in the order the enum declares them")
    void releaseTypesAreListed() {
        AccountLicense chatty = accountLicenses.owned(owner).getFirst();

        assertEquals(List.of("STABLE", "DEV"), chatty.releaseTypes());
    }

    @Test
    @DisplayName("A license with no access granted lists no release types rather than failing")
    void licenseWithoutAccessHasNoReleaseTypes() {
        AccountLicense elsewhere = accountLicenses.owned(owner).getLast();

        assertTrue(elsewhere.releaseTypes().isEmpty());
    }

    @Test
    @DisplayName("Somebody who holds nothing sees nothing")
    void strangerSeesNothing() {
        assertTrue(accountLicenses.owned(stranger()).isEmpty());
        assertTrue(accountLicenses.shared(stranger()).isEmpty());
    }

    @Test
    @DisplayName("A shared license shows up for the sharee and not among what they own")
    void sharedIsSeparateFromOwned() {
        accountLicenses.addSharee(chattyLicense, sharee);

        List<AccountLicense> shared = accountLicenses.shared(sharee);
        assertEquals(1, shared.size());
        assertEquals("Chatty", shared.getFirst().productName());
        assertEquals(AccountLicense.Role.SHAREE, shared.getFirst().role());
        assertEquals(owner, shared.getFirst().ownerAccountId());
        assertTrue(accountLicenses.owned(sharee).isEmpty());
    }

    @Test
    @DisplayName("The sharee count and the guild's cap are both reported")
    void shareeCountAndCap() {
        accountLicenses.addSharee(chattyLicense, sharee);
        accountLicenses.addSharee(chattyLicense, stranger());

        AccountLicense chatty = accountLicenses.owned(owner).getFirst();
        assertEquals(2, chatty.shareesUsed());
        assertEquals(3, chatty.shareesCap());
    }

    @Test
    @DisplayName("A guild that set no cap reads as none rather than as an error")
    void missingSettingsMeanNoCap() {
        AccountLicense elsewhere = accountLicenses.owned(owner).getLast();

        assertEquals(0, elsewhere.shareesCap());
    }

    @Test
    @DisplayName("Sharing with the same person twice adds them once")
    void addShareeIsIdempotent() {
        assertTrue(accountLicenses.addSharee(chattyLicense, sharee));

        assertFalse(accountLicenses.addSharee(chattyLicense, sharee));

        assertEquals(List.of(sharee), accountLicenses.sharees(chattyLicense));
    }

    @Test
    @DisplayName("Revoking takes the license away from the sharee")
    void removeSharee() {
        accountLicenses.addSharee(chattyLicense, sharee);

        assertTrue(accountLicenses.removeSharee(chattyLicense, sharee));

        assertTrue(accountLicenses.shared(sharee).isEmpty());
        assertFalse(accountLicenses.removeSharee(chattyLicense, sharee));
    }

    @Test
    @DisplayName("A holder reads their own license, and is told which side of it they are on")
    void forHolderReportsTheRole() {
        accountLicenses.addSharee(chattyLicense, sharee);

        assertEquals(
                AccountLicense.Role.OWNER,
                accountLicenses.forHolder(chattyLicense, owner).orElseThrow().role());
        assertEquals(
                AccountLicense.Role.SHAREE,
                accountLicenses.forHolder(chattyLicense, sharee).orElseThrow().role());
    }

    @Test
    @DisplayName("Somebody who holds no part of a license cannot read it")
    void forHolderRefusesAStranger() {
        assertTrue(accountLicenses.forHolder(chattyLicense, stranger()).isEmpty());
        assertTrue(accountLicenses.keyForHolder(chattyLicense, stranger()).isEmpty());
    }

    @Test
    @DisplayName("The key is handed to the owner and to a sharee, and to nobody else")
    void keyGoesToHoldersOnly() {
        accountLicenses.addSharee(chattyLicense, sharee);

        assertEquals(
                "KEY-CHATTY", accountLicenses.keyForHolder(chattyLicense, owner).orElseThrow());
        assertEquals(
                "KEY-CHATTY",
                accountLicenses.keyForHolder(chattyLicense, sharee).orElseThrow());
        assertTrue(accountLicenses.keyForHolder(chattyLicense, stranger()).isEmpty());
    }

    /** An account that holds nothing, minted fresh so it cannot collide with the cast. */
    private static int stranger() {
        return de.chojo.lyna.data.access.Accounts.accountIdForDiscord(900_000L + counter++);
    }

    private static long counter = 0;
}
