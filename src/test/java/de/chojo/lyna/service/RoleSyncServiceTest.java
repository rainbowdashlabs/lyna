/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.TestConf;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.roles.RoleSync;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.repository.RepositoryTestBase;
import de.chojo.nexus.NexusRest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deleting a license and clearing its shares, and what each asks of the Discord roles behind them.
 *
 * <p>The gateway is stood in for by a {@link RoleSync} that records what it was asked and what the
 * rows said at the moment of asking. That is the whole point: both call sites changed when the data
 * layer stopped carrying a gateway object, and one of them changed on purpose.
 */
class RoleSyncServiceTest extends RepositoryTestBase {
    private static final long GUILD = 5001L;
    private static final long OWNER = 900L;
    private static final long SHAREE = 901L;

    private RecordingRoleSync roles;
    private LicenseGuild licenseGuild;
    private int licenseId;

    /**
     * Records each request, and with it whether the license rows still granted that id anything when
     * the request was made - which is how the ordering of the check against the delete is observed.
     */
    private final class RecordingRoleSync implements RoleSync {
        private final List<String> calls = new ArrayList<>();
        private final List<Boolean> entitledWhenAsked = new ArrayList<>();

        @Override
        public void revoke(long guildId, long discordId, Product product) {
            calls.add("revoke:" + discordId);
            entitledWhenAsked.add(stillEntitled(discordId, product.id()));
        }

        @Override
        public void revokeIfUnentitled(long guildId, long discordId, Product product) {
            calls.add("revokeIfUnentitled:" + discordId);
            entitledWhenAsked.add(stillEntitled(discordId, product.id()));
        }

        private boolean stillEntitled(long discordId, int productId) {
            return accountLicenses
                    .entitledProductIds(accountLinks.accountIdForDiscord(discordId))
                    .contains(productId);
        }
    }

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "license_invite",
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "product",
                "account_identity",
                "account");
        roles = new RecordingRoleSync();

        Conf configuration = TestConf.defaults();
        Guilds guilds = new Guilds(Mockito.mock(NexusRest.class), configuration, accountLinks);
        guilds.roles(roles);
        licenseGuild = guilds.guild(GUILD);

        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            int productId = insert(statement, """
                    INSERT INTO product (guild_id, name, role, free) VALUES (%d, 'Roles', 77, FALSE) RETURNING id
                    """.formatted(GUILD));
            licenseId = insert(statement, """
                    INSERT INTO license (product_id, user_identifier, key)
                    VALUES (%d, 'owner@example.invalid', 'ROLE-KEY') RETURNING id
                    """.formatted(productId));
            statement.execute("INSERT INTO %s.user_license (account_id, license_id) VALUES (%d, %d)"
                    .formatted(schemaName, accountLinks.accountIdForDiscord(OWNER), licenseId));
            statement.execute("INSERT INTO %s.user_sub_license (account_id, license_id) VALUES (%d, %d)"
                    .formatted(schemaName, accountLinks.accountIdForDiscord(SHAREE), licenseId));
        }
    }

    private static int insert(Statement statement, String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private License license() {
        return licenseGuild.licenses().byId(licenseId).orElseThrow();
    }

    @Test
    @DisplayName("Clearing the shares asks about every sharee, and about nobody else")
    void clearAsksAboutEverySharee() {
        licenseSharing.clearSharees(license());

        assertEquals(List.of("revokeIfUnentitled:" + SHAREE), roles.calls);
    }

    @Test
    @DisplayName("A sharee is asked about only once their share is gone")
    void shareeIsAskedAfterTheRowsGo() {
        licenseSharing.clearSharees(license());

        // False is the whole point: asked while the share was still there, the answer was always
        // "entitled" and the role was never taken back.
        assertEquals(List.of(false), roles.entitledWhenAsked);
    }

    @Test
    @DisplayName("Clearing the shares takes the rows with it")
    void clearRemovesTheRows() throws SQLException {
        licenseSharing.clearSharees(license());

        assertEquals(0, countRows("user_sub_license"));
        assertTrue(
                accountLicenses.shared(accountLinks.accountIdForDiscord(SHAREE)).isEmpty());
    }

    @Test
    @DisplayName("Deleting a license takes the owner's role back whatever else is true")
    void deleteRevokesTheOwnerUnconditionally() {
        licenseService.delete(license());

        assertTrue(roles.calls.contains("revoke:" + OWNER));
    }

    @Test
    @DisplayName("Deleting clears the shares first, so a sharee is asked about too")
    void deleteClearsTheSharesFirst() {
        licenseService.delete(license());

        assertEquals(List.of("revokeIfUnentitled:" + SHAREE, "revoke:" + OWNER), roles.calls);
    }

    @Test
    @DisplayName("Deleting takes the license and everything hanging off it")
    void deleteRemovesEverything() throws SQLException {
        licenseService.delete(license());

        assertEquals(0, countRows("license"));
        assertEquals(0, countRows("user_license"));
        assertEquals(0, countRows("user_sub_license"));
    }

    @Test
    @DisplayName("Somebody who owns a license of their own keeps it when a share is cleared")
    void ownerIsUnaffectedByAShareBeingCleared() {
        licenseSharing.clearSharees(license());

        assertTrue(accountLicenses
                .entitledProductIds(accountLinks.accountIdForDiscord(OWNER))
                .contains(license().product().id()));
        assertTrue(accountLicenses
                .entitledProductIds(accountLinks.accountIdForDiscord(SHAREE))
                .isEmpty());
    }

    @Test
    @DisplayName("Without a gateway nothing is asked of Discord, and the rows still go")
    void withoutAGatewayTheRowsStillGo() throws SQLException {
        Conf configuration = TestConf.defaults();
        Guilds botless = new Guilds(Mockito.mock(NexusRest.class), configuration, accountLinks);
        assertFalse(botless.roles() == roles);

        licenseService.delete(botless.guild(GUILD).licenses().byId(licenseId).orElseThrow());

        assertEquals(0, countRows("license"));
        assertTrue(roles.calls.isEmpty());
    }

    @Test
    @DisplayName("A sharee with no Discord is listed too, so nothing under-reports the shares")
    void shareesIncludeWebOnlyHolders() {
        var webOnly = accountService.register("web-only@example.invalid", "hash");
        usernameService.setUsername(webOnly.id(), "ada");
        accountLicenses.addSharee(licenseId, webOnly.id());

        var sharees = licenseSharing.sharees(license());

        assertEquals(2, sharees.size());
        assertEquals(1, licenseSharing.shareeDiscordIds(license()).size());
        assertTrue(sharees.stream().anyMatch(s -> s.discordId() != null && s.discordId() == SHAREE));
        var web =
                sharees.stream().filter(s -> s.discordId() == null).findFirst().orElseThrow();
        assertTrue(web.name().startsWith("ada#"));
        assertEquals(web.name(), web.display());
    }

    @Test
    @DisplayName("The cap counts web-only sharees and standing invites, not just Discord ones")
    void shareCountCoversEverybody() {
        assertEquals(1, licenseSharing.shareCount(license()));

        var webOnly = accountService.register("counted@example.invalid", "hash");
        accountLicenses.addSharee(licenseId, webOnly.id());
        assertEquals(2, licenseSharing.shareCount(license()));

        licenseInvites.invite(licenseId, "waiting@example.invalid");
        assertEquals(3, licenseSharing.shareCount(license()));

        licenseInvites.withdraw(licenseId, "waiting@example.invalid");
        assertEquals(2, licenseSharing.shareCount(license()));
    }

    @Test
    @DisplayName("A sharee nobody has named is listed by something, rather than by nothing")
    void unnamedShareeStillListed() {
        var unnamed = accountService.register("unnamed@example.invalid", "hash");
        accountLicenses.addSharee(licenseId, unnamed.id());

        var web = licenseSharing.sharees(license()).stream()
                .filter(s -> s.discordId() == null)
                .findFirst()
                .orElseThrow();

        assertEquals("account " + unnamed.id(), web.name());
    }
}
