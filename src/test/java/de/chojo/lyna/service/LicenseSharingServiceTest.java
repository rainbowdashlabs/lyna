package de.chojo.lyna.service;

import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.AccountLicense;
import de.chojo.lyna.data.dao.account.AccountIdentity;
import de.chojo.lyna.data.dao.account.DownloadLogEntry;
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
 * What an account may do with a license it holds: see the ones its Discord id carries, share them
 * up to the guild's cap, revoke a share, and read the download history the license produced.
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
        clear("download_log", "user_sub_license", "user_license", "license_access", "license",
                "license_settings", "download", "download_type", "product",
                "account_identity", "account");

        owner = accounts.create("owner@example.invalid", "hash");
        sharee = accounts.create("sharee@example.invalid", "hash");
        accounts.link(owner.id(), OWNER_DISCORD, AccountIdentity.Verification.OAUTH);
        accounts.link(sharee.id(), SHAREE_DISCORD, AccountIdentity.Verification.OAUTH);

        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO %s.license_settings (guild_id, shares) VALUES (%d, 2)"
                    .formatted(schemaName, GUILD));
            productId = insert(statement, "INSERT INTO product (guild_id, name, role) VALUES (%d, 'Chatty', 1) RETURNING id"
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
            statement.execute("INSERT INTO %s.user_license (user_id, license_id) VALUES (%d, %d)"
                    .formatted(schemaName, OWNER_DISCORD, licenseId));
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
    @DisplayName("An account with no Discord link holds no licenses, rather than being refused")
    void unlinkedAccountHoldsNothing() {
        Account unlinked = accounts.create("unlinked@example.invalid", "hash");

        assertTrue(discordIdOf(unlinked).isEmpty());
    }

    @Test
    @DisplayName("Unlinking hides the licenses, and linking again brings them back")
    void unlinkHidesAndRelinkRestores() {
        assertEquals(1, accountLicenses.owned(discordIdOf(owner).orElseThrow()).size());

        accounts.unlink(owner.id());
        assertTrue(discordIdOf(owner).isEmpty());

        accounts.link(owner.id(), OWNER_DISCORD, AccountIdentity.Verification.OAUTH);
        assertEquals(1, accountLicenses.owned(discordIdOf(owner).orElseThrow()).size());
    }

    @Test
    @DisplayName("Sharing hands the license to the other account without giving it away")
    void sharingKeepsOwnership() {
        accountLicenses.addSharee(licenseId, SHAREE_DISCORD);

        assertEquals(1, accountLicenses.owned(OWNER_DISCORD).size());
        assertTrue(accountLicenses.owned(SHAREE_DISCORD).isEmpty());
        assertEquals(1, accountLicenses.shared(SHAREE_DISCORD).size());
        assertTrue(accountLicenses.shared(OWNER_DISCORD).isEmpty());
    }

    @Test
    @DisplayName("The cap is the guild's, and it is reached when the sharees fill it")
    void capIsReported() {
        accountLicenses.addSharee(licenseId, SHAREE_DISCORD);
        accountLicenses.addSharee(licenseId, 702L);

        AccountLicense license = accountLicenses.owned(OWNER_DISCORD).getFirst();
        assertEquals(2, license.shareesUsed());
        assertEquals(2, license.shareesCap());
        assertTrue(license.shareesUsed() >= license.shareesCap());
    }

    @Test
    @DisplayName("Revoking takes the license back and frees a place under the cap")
    void revokingFreesAPlace() {
        accountLicenses.addSharee(licenseId, SHAREE_DISCORD);
        accountLicenses.addSharee(licenseId, 702L);

        accountLicenses.removeSharee(licenseId, SHAREE_DISCORD);

        assertTrue(accountLicenses.shared(SHAREE_DISCORD).isEmpty());
        assertEquals(1, accountLicenses.owned(OWNER_DISCORD).getFirst().shareesUsed());
    }

    @Test
    @DisplayName("A sharee cannot manage the sharees: they are not the owner")
    void shareeIsNotTheOwner() {
        accountLicenses.addSharee(licenseId, SHAREE_DISCORD);

        AccountLicense asSharee = accountLicenses.forHolder(licenseId, SHAREE_DISCORD).orElseThrow();

        assertEquals(AccountLicense.Role.SHAREE, asSharee.role());
        assertEquals(OWNER_DISCORD, asSharee.ownerDiscordId());
    }

    @Test
    @DisplayName("Somebody holding no part of the license reads nothing about it")
    void strangerReadsNothing() {
        assertTrue(accountLicenses.forHolder(licenseId, 999L).isEmpty());
        assertTrue(accountLicenses.keyForHolder(licenseId, 999L).isEmpty());
    }

    @Test
    @DisplayName("The owner sees every holder's downloads on the license")
    void ownerSeesEveryDownload() {
        downloadLog.record(owner.id(), OWNER_DISCORD, licenseId, productId, downloadId, "1.0.0", "license", null, null);
        downloadLog.record(sharee.id(), SHAREE_DISCORD, licenseId, productId, downloadId, "1.0.1", "sub_license", null, null);

        List<DownloadLogEntry> seen = downloadLog.recentForLicense(licenseId, null, 10);

        assertEquals(2, seen.size());
        assertEquals(List.of("1.0.1", "1.0.0"), seen.stream().map(DownloadLogEntry::version).toList());
    }

    @Test
    @DisplayName("A sharee sees only their own downloads on the license")
    void shareeSeesOnlyTheirOwn() {
        downloadLog.record(owner.id(), OWNER_DISCORD, licenseId, productId, downloadId, "1.0.0", "license", null, null);
        downloadLog.record(sharee.id(), SHAREE_DISCORD, licenseId, productId, downloadId, "1.0.1", "sub_license", null, null);

        List<DownloadLogEntry> seen = downloadLog.recentForLicense(licenseId, sharee.id(), 10);

        assertEquals(List.of("1.0.1"), seen.stream().map(DownloadLogEntry::version).toList());
    }

    @Test
    @DisplayName("Downloads on another license are not mixed in")
    void otherLicensesAreNotMixedIn() throws SQLException {
        int otherLicense;
        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            otherLicense = insert(statement, """
                    INSERT INTO license (product_id, user_identifier, key)
                    VALUES (%d, 'someone@example.invalid', 'KEY-2') RETURNING id
                    """.formatted(productId));
        }
        downloadLog.record(owner.id(), OWNER_DISCORD, licenseId, productId, downloadId, "mine", "license", null, null);
        downloadLog.record(owner.id(), OWNER_DISCORD, otherLicense, productId, downloadId, "other", "license", null, null);

        assertEquals(List.of("mine"), downloadLog.recentForLicense(licenseId, null, 10).stream()
                .map(DownloadLogEntry::version).toList());
    }

    @Test
    @DisplayName("Deleting the owner's account leaves the license with the Discord id it belongs to")
    void deletingTheAccountKeepsTheLicense() {
        accounts.delete(owner.id());

        assertFalse(accountLicenses.owned(OWNER_DISCORD).isEmpty());
    }
}
