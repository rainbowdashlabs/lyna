/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.repository.DownloadRepository;
import de.chojo.lyna.feature.download.repository.DownloadTypeRepository;
import de.chojo.lyna.feature.guild.repository.GuildSettingsRepository;
import de.chojo.lyna.feature.mail.repository.MailingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statements pulled out of the row objects when the features were sliced apart.
 *
 * <p>They were covered only through whatever happened to call the row before, which is to say barely.
 */
class ExtractedRepositoriesTest extends RepositoryTestBase {
    private static final long GUILD = 4201L;

    private final DownloadRepository downloads = new DownloadRepository();
    private final DownloadTypeRepository downloadTypes = new DownloadTypeRepository();
    private final MailingRepository mailings = new MailingRepository();
    private final GuildSettingsRepository guildSettings = new GuildSettingsRepository();

    private int productId;
    private int typeId;
    private int downloadId;
    private int mailingId;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "download_stat",
                "role_access",
                "download",
                "download_type",
                "mail_products",
                "trial_settings",
                "license_settings",
                "product");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            productId = insert(
                    statement,
                    "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, 'Widget', 5) RETURNING id"
                            .formatted(schemaName, GUILD));
            typeId = insert(statement, """
                    INSERT INTO %s.download_type (guild_id, name, description, release_type)
                    VALUES (%d, 'stable', 'the stable one', 'STABLE') RETURNING id
                    """.formatted(schemaName, GUILD));
            downloadId = insert(statement, """
                    INSERT INTO %s.download (product_id, type_id, repository, group_id, artifact_id)
                    VALUES (%d, %d, 'releases', 'de.chojo', 'widget') RETURNING id
                    """.formatted(schemaName, productId, typeId));
            mailingId = insert(statement, """
                    INSERT INTO %s.mail_products (product_id, name, mail_text)
                    VALUES (%d, 'Widget mail', 'body') RETURNING id
                    """.formatted(schemaName, productId));
        }
    }

    private static int insert(Statement statement, String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private String one(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    @Test
    @DisplayName("A download's column is written for that product and type alone")
    void downloadSetIsScoped() throws SQLException {
        assertTrue(downloads.set(productId, typeId, "classifier", c -> c.bind("all")));
        assertEquals("all", one("SELECT classifier FROM %s.download WHERE id = %d".formatted(schemaName, downloadId)));
        assertFalse(
                downloads.set(productId, typeId + 999, "classifier", c -> c.bind("nope")),
                "another type is another download");
    }

    @Test
    @DisplayName("Fetching a version is counted, and counted again the next time")
    void downloadsAreCounted() throws SQLException {
        downloads.recordDownload(downloadId, "1.0.0");
        assertEquals(
                "1",
                one("SELECT count FROM %s.download_stat WHERE download_id = %d AND version = '1.0.0'"
                        .formatted(schemaName, downloadId)));

        downloads.recordDownload(downloadId, "1.0.0");
        assertEquals(
                "2",
                one("SELECT count FROM %s.download_stat WHERE download_id = %d AND version = '1.0.0'"
                        .formatted(schemaName, downloadId)),
                "the same version on the same day adds to the row rather than making another");
    }

    @Test
    @DisplayName("A role is let onto a release type, and taken off again")
    void rolesAreGrantedAndRevoked() {
        assertTrue(downloads.grantRole(42L, productId, ReleaseType.DEV));
        assertFalse(downloads.grantRole(42L, productId, ReleaseType.DEV), "twice grants nothing new");
        assertEquals(
                java.util.List.of(ReleaseType.DEV), productRepository.accessByRoles(productId, java.util.List.of(42L)));

        assertTrue(downloads.revokeRole(42L, productId, ReleaseType.DEV));
        assertTrue(productRepository
                .accessByRoles(productId, java.util.List.of(42L))
                .isEmpty());
    }

    @Test
    @DisplayName("A download's row goes when it is deleted")
    void downloadIsDeleted() throws SQLException {
        assertTrue(downloads.delete(productId, typeId));
        assertNull(one("SELECT id FROM %s.download WHERE id = %d".formatted(schemaName, downloadId)));
        assertFalse(downloads.delete(productId, typeId));
    }

    @Test
    @DisplayName("A download type is written and deleted, and only by its own guild")
    void downloadTypesAreScopedToTheGuild() throws SQLException {
        assertTrue(downloadTypes.set(typeId, "description", c -> c.bind("renamed")));
        assertEquals(
                "renamed", one("SELECT description FROM %s.download_type WHERE id = %d".formatted(schemaName, typeId)));

        downloads.delete(productId, typeId);
        assertFalse(downloadTypes.delete(GUILD + 1, typeId), "another guild deletes nothing");
        assertTrue(downloadTypes.delete(GUILD, typeId));
    }

    @Test
    @DisplayName("A mail template's text and its blocks are written separately")
    void mailingsAreWritten() throws SQLException {
        assertTrue(mailings.set(mailingId, "name", c -> c.bind("Renamed mail")));
        assertEquals(
                "Renamed mail",
                one("SELECT name FROM %s.mail_products WHERE id = %d".formatted(schemaName, mailingId)));

        assertTrue(mailings.setBlocks(mailingId, "[{\"type\":\"paragraph\",\"text\":\"hello\"}]"));
        assertTrue(one("SELECT blocks FROM %s.mail_products WHERE id = %d".formatted(schemaName, mailingId))
                .contains("hello"));
    }

    @Test
    @DisplayName("A guild that has never had settings gets a row when it first decides something")
    void guildSettingsUpsert() throws SQLException {
        assertTrue(guildSettings.setShares(GUILD, 5));
        assertEquals(
                "5", one("SELECT shares FROM %s.license_settings WHERE guild_id = %d".formatted(schemaName, GUILD)));

        assertTrue(guildSettings.setShares(GUILD, 9), "and changing it keeps the one row");
        assertEquals(
                "9", one("SELECT shares FROM %s.license_settings WHERE guild_id = %d".formatted(schemaName, GUILD)));

        assertTrue(guildSettings.setAdminRole(GUILD, 4242L));
        assertEquals(
                "4242",
                one("SELECT admin_role_id FROM %s.license_settings WHERE guild_id = %d".formatted(schemaName, GUILD)));
        assertEquals(
                "9",
                one("SELECT shares FROM %s.license_settings WHERE guild_id = %d".formatted(schemaName, GUILD)),
                "writing one setting leaves the other alone");
    }

    @Test
    @DisplayName("Trial settings upsert the same way")
    void trialSettingsUpsert() throws SQLException {
        assertTrue(guildSettings.setTrial(
                "server_time", c -> c.bind(GUILD).bind(60).bind(60)));
        assertEquals(
                "60",
                one("SELECT server_time FROM %s.trial_settings WHERE guild_id = %d".formatted(schemaName, GUILD)));

        assertTrue(guildSettings.setTrial(
                "server_time", c -> c.bind(GUILD).bind(90).bind(90)));
        assertEquals(
                "90",
                one("SELECT server_time FROM %s.trial_settings WHERE guild_id = %d".formatted(schemaName, GUILD)));
    }
}
