/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.data.dao.account.DownloadLogEntry;
import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.entity.AccountLicense;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an account may do with a license it holds: see it, share it up to the guild's cap, revoke a
 * share, and read the download history the license produced.
 *
 * <p>All of it by account. Discord is one way to arrive at an account and not a condition of holding
 * anything, which is what the unlinking cases below are about.
 */
class LicenseSharingServiceTest extends RepositoryTestBase {
    private static final long GUILD = 2001L;
    private static final long OWNER_DISCORD = 700L;
    private static final long SHAREE_DISCORD = 701L;

    private Account owner;
    private Account sharee;
    private int licenseId;
    private int productId;
    private int downloadId;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "download_log",
                "license_invite",
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "license_settings",
                "download",
                "download_type",
                "product",
                "account_identity",
                "account");

        owner = accountService.register("owner@example.invalid", "hash");
        sharee = accountService.register("sharee@example.invalid", "hash");
        accounts.link(owner.id(), OWNER_DISCORD, AccountIdentity.Verification.OAUTH);
        accounts.link(sharee.id(), SHAREE_DISCORD, AccountIdentity.Verification.OAUTH);

        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO %s.license_settings (guild_id, shares) VALUES (%d, 2)".formatted(schemaName, GUILD));
            productId = insert(
                    statement,
                    "INSERT INTO product (guild_id, name, role) VALUES (%d, 'Chatty', 1) RETURNING id"
                            .formatted(GUILD));
            int typeId = insert(statement, """
                    INSERT INTO download_type (guild_id, name, description, release_type)
                    VALUES (%d, 'Jar', 'Plain jar', 'STABLE') RETURNING id
                    """.formatted(GUILD));
            downloadId = insert(statement, """
                    INSERT INTO download (product_id, type_id, repository, group_id, artifact_id)
                    VALUES (%d, %d, 'releases', 'de.chojo', 'chatty') RETURNING id
                    """.formatted(productId, typeId));
            licenseId = insert(statement, """
                    INSERT INTO license (product_id, user_identifier, key)
                    VALUES (%d, 'owner@example.invalid', 'KEY-1') RETURNING id
                    """.formatted(productId));
            statement.execute("INSERT INTO %s.user_license (account_id, license_id) VALUES (%d, %d)"
                    .formatted(schemaName, owner.id(), licenseId));
        }
    }

    private static int insert(Statement statement, String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    /**
     * @return the Discord id the account is linked to, which is what its licenses are keyed by
     */
    private Optional<Long> discordIdOf(Account account) {
        return accounts.findLinkByAccountId(account.id()).map(AccountIdentity::externalIdAsLong);
    }

    @Test
    @DisplayName("An account that never linked Discord holds nothing, rather than being refused")
    void unlinkedAccountHoldsNothing() {
        Account unlinked = accountService.register("unlinked@example.invalid", "hash");

        assertTrue(discordIdOf(unlinked).isEmpty());
        assertTrue(accountLicenses.owned(unlinked.id()).isEmpty());
        assertTrue(accountLicenses.shared(unlinked.id()).isEmpty());
    }

    @Test
    @DisplayName("Unlinking Discord does not take the licenses away: they belong to the account")
    void unlinkKeepsTheLicenses() {
        assertEquals(1, accountLicenses.owned(owner.id()).size());

        accounts.unlink(owner.id());

        assertTrue(discordIdOf(owner).isEmpty());
        assertEquals(1, accountLicenses.owned(owner.id()).size());
    }

    @Test
    @DisplayName("An account with no Discord at all can be shared with, and reads the license")
    void shareeNeedsNoDiscord() {
        Account webOnly = accountService.register("web-only@example.invalid", "hash");

        assertTrue(accountLicenses.addSharee(licenseId, webOnly.id()));

        assertEquals(1, accountLicenses.shared(webOnly.id()).size());
        assertEquals(
                "KEY-1", accountLicenses.keyForHolder(licenseId, webOnly.id()).orElseThrow());
        assertTrue(discordIdOf(webOnly).isEmpty());
    }

    @Test
    @DisplayName("Sharing hands the license to the other account without giving it away")
    void sharingKeepsOwnership() {
        accountLicenses.addSharee(licenseId, sharee.id());

        assertEquals(1, accountLicenses.owned(owner.id()).size());
        assertTrue(accountLicenses.owned(sharee.id()).isEmpty());
        assertEquals(1, accountLicenses.shared(sharee.id()).size());
        assertTrue(accountLicenses.shared(owner.id()).isEmpty());
    }

    @Test
    @DisplayName("The cap is the guild's, and it is reached when the sharees fill it")
    void capIsReported() {
        accountLicenses.addSharee(licenseId, sharee.id());
        accountLicenses.addSharee(licenseId, accountService.register(null, null).id());

        AccountLicense license = accountLicenses.owned(owner.id()).getFirst();
        assertEquals(2, license.shareesUsed());
        assertEquals(2, license.shareesCap());
        assertTrue(license.shareesUsed() >= license.shareesCap());
    }

    @Test
    @DisplayName("Revoking takes the license back and frees a place under the cap")
    void revokingFreesAPlace() {
        accountLicenses.addSharee(licenseId, sharee.id());
        accountLicenses.addSharee(licenseId, accountService.register(null, null).id());

        accountLicenses.removeSharee(licenseId, sharee.id());

        assertTrue(accountLicenses.shared(sharee.id()).isEmpty());
        assertEquals(1, accountLicenses.owned(owner.id()).getFirst().shareesUsed());
    }

    @Test
    @DisplayName("A sharee cannot manage the sharees: they are not the owner")
    void shareeIsNotTheOwner() {
        accountLicenses.addSharee(licenseId, sharee.id());

        AccountLicense asSharee =
                accountLicenses.forHolder(licenseId, sharee.id()).orElseThrow();

        assertEquals(AccountLicense.Role.SHAREE, asSharee.role());
        assertEquals(owner.id(), asSharee.ownerAccountId());
    }

    @Test
    @DisplayName("Somebody holding no part of the license reads nothing about it")
    void strangerReadsNothing() {
        Account stranger = accountService.register("stranger@example.invalid", "hash");

        assertTrue(accountLicenses.forHolder(licenseId, stranger.id()).isEmpty());
        assertTrue(accountLicenses.keyForHolder(licenseId, stranger.id()).isEmpty());
    }

    @Test
    @DisplayName("The owner sees every holder's downloads on the license")
    void ownerSeesEveryDownload() {
        downloadLog.record(owner.id(), OWNER_DISCORD, licenseId, productId, downloadId, "1.0.0", "license", null, null);
        downloadLog.record(
                sharee.id(), SHAREE_DISCORD, licenseId, productId, downloadId, "1.0.1", "sub_license", null, null);

        List<DownloadLogEntry> seen = downloadLog.recentForLicense(licenseId, null, 10);

        assertEquals(2, seen.size());
        assertEquals(
                List.of("1.0.1", "1.0.0"),
                seen.stream().map(DownloadLogEntry::version).toList());
    }

    @Test
    @DisplayName("A sharee sees only their own downloads on the license")
    void shareeSeesOnlyTheirOwn() {
        downloadLog.record(owner.id(), OWNER_DISCORD, licenseId, productId, downloadId, "1.0.0", "license", null, null);
        downloadLog.record(
                sharee.id(), SHAREE_DISCORD, licenseId, productId, downloadId, "1.0.1", "sub_license", null, null);

        List<DownloadLogEntry> seen = downloadLog.recentForLicense(licenseId, sharee.id(), 10);

        assertEquals(
                List.of("1.0.1"), seen.stream().map(DownloadLogEntry::version).toList());
    }

    @Test
    @DisplayName("Downloads on another license are not mixed in")
    void otherLicensesAreNotMixedIn() throws SQLException {
        int otherLicense;
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            otherLicense = insert(statement, """
                    INSERT INTO license (product_id, user_identifier, key)
                    VALUES (%d, 'someone@example.invalid', 'KEY-2') RETURNING id
                    """.formatted(productId));
        }
        downloadLog.record(owner.id(), OWNER_DISCORD, licenseId, productId, downloadId, "mine", "license", null, null);
        downloadLog.record(
                owner.id(), OWNER_DISCORD, otherLicense, productId, downloadId, "other", "license", null, null);

        assertEquals(
                List.of("mine"),
                downloadLog.recentForLicense(licenseId, null, 10).stream()
                        .map(DownloadLogEntry::version)
                        .toList());
    }

    @Test
    @DisplayName("Deleting the owner's account releases the license rather than destroying it")
    void deletingTheAccountReleasesTheLicense() throws SQLException {
        accounts.delete(owner.id());

        assertTrue(accountLicenses.owned(owner.id()).isEmpty());
        assertEquals(0, countRows("user_license"));
        assertEquals(1, countRows("license"));

        Account returning = accountService.register("returning@example.invalid", "hash");
        assertTrue(accountLicenses.addSharee(licenseId, returning.id()));
        assertEquals(
                "KEY-1", accountLicenses.keyForHolder(licenseId, returning.id()).orElseThrow());
    }

    @Test
    @DisplayName("Deleting a sharee's account ends their share and frees a place under the cap")
    void deletingAShareeFreesAPlace() {
        accountLicenses.addSharee(licenseId, sharee.id());
        assertEquals(1, accountLicenses.owned(owner.id()).getFirst().shareesUsed());

        accounts.delete(sharee.id());

        assertEquals(0, accountLicenses.owned(owner.id()).getFirst().shareesUsed());
    }

    @Test
    @DisplayName("An address nobody has an account for can be invited, and waits")
    void inviteWaitsForAnAccount() {
        assertTrue(licenseInvites.invite(licenseId, "newcomer@example.invalid"));

        assertEquals(1, licenseInvites.standing(licenseId).size());
        assertEquals(
                "newcomer@example.invalid",
                licenseInvites.standing(licenseId).getFirst().email());
    }

    @Test
    @DisplayName("Verifying the invited address hands the licence over")
    void verifyingBindsTheInvite() {
        licenseInvites.invite(licenseId, "newcomer@example.invalid");
        Account newcomer = accountService.register("newcomer@example.invalid", "hash");

        assertTrue(accountLicenses.shared(newcomer.id()).isEmpty());

        List<Integer> bound = accountEmailService.confirm(newcomer.id(), "newcomer@example.invalid");

        assertEquals(List.of(licenseId), bound);
        assertEquals(1, accountLicenses.shared(newcomer.id()).size());
        assertEquals(
                "KEY-1", accountLicenses.keyForHolder(licenseId, newcomer.id()).orElseThrow());
        assertTrue(licenseInvites.standing(licenseId).isEmpty());
    }

    @Test
    @DisplayName("Merely registering the address is not enough: it has to be proved")
    void registeringDoesNotBind() {
        licenseInvites.invite(licenseId, "unproven@example.invalid");
        Account unproven = accountService.register("unproven@example.invalid", "hash");

        assertTrue(accountLicenses.shared(unproven.id()).isEmpty());
        assertEquals(1, licenseInvites.standing(licenseId).size());
    }

    @Test
    @DisplayName("Verifying a different address does not collect somebody else's invite")
    void bindingIsPerAddress() {
        licenseInvites.invite(licenseId, "invited@example.invalid");
        Account other = accountService.register("other@example.invalid", "hash");

        assertEquals(List.of(), accountEmailService.confirm(other.id(), "other@example.invalid"));
        assertTrue(accountLicenses.shared(other.id()).isEmpty());
        assertEquals(1, licenseInvites.standing(licenseId).size());
    }

    @Test
    @DisplayName("The address is matched however it was capitalised")
    void bindingIgnoresCase() {
        licenseInvites.invite(licenseId, "Mixed.Case@Example.invalid");
        Account newcomer = accountService.register("mixed.case@example.invalid", "hash");

        assertEquals(List.of(licenseId), accountEmailService.confirm(newcomer.id(), "mixed.case@example.invalid"));
    }

    @Test
    @DisplayName("A standing invite holds a place under the cap, so the world cannot be invited")
    void invitesCountAgainstTheCap() {
        accountLicenses.addSharee(licenseId, sharee.id());
        licenseInvites.invite(licenseId, "waiting@example.invalid");

        AccountLicense license = accountLicenses.owned(owner.id()).getFirst();
        assertEquals(2, license.shareesUsed());
        assertEquals(2, license.shareesCap());
    }

    @Test
    @DisplayName("Withdrawing an invite frees the place it held")
    void withdrawingFreesThePlace() {
        licenseInvites.invite(licenseId, "waiting@example.invalid");
        assertEquals(1, accountLicenses.owned(owner.id()).getFirst().shareesUsed());

        assertTrue(licenseInvites.withdraw(licenseId, "WAITING@example.invalid"));

        assertEquals(0, accountLicenses.owned(owner.id()).getFirst().shareesUsed());
    }

    @Test
    @DisplayName("Inviting the same address again renews it rather than taking a second place")
    void reinvitingRenews() {
        assertTrue(licenseInvites.invite(licenseId, "waiting@example.invalid"));
        assertFalse(licenseInvites.invite(licenseId, "waiting@example.invalid"));

        assertEquals(1, licenseInvites.standing(licenseId).size());
        assertEquals(1, accountLicenses.owned(owner.id()).getFirst().shareesUsed());
    }

    @Test
    @DisplayName("An invite that ran out holds no place and hands over nothing")
    void expiredInvitesAreIgnored() {
        licenseInvites.invite(licenseId, "late@example.invalid");
        expireInvites();

        assertEquals(0, accountLicenses.owned(owner.id()).getFirst().shareesUsed());
        assertTrue(licenseInvites.standing(licenseId).isEmpty());

        Account late = accountService.register("late@example.invalid", "hash");
        assertEquals(List.of(), accountEmailService.confirm(late.id(), "late@example.invalid"));
        assertTrue(accountLicenses.shared(late.id()).isEmpty());
    }

    private static void expireInvites() {
        de.chojo.sadu.queries.api.query.Query.query("UPDATE license_invite SET expires_at = now() - INTERVAL '1 day'")
                .single(de.chojo.sadu.queries.api.call.Call.call())
                .update();
    }
}
