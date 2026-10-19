/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.guild.repository.GuildSettingsRepository;
import de.chojo.lyna.feature.mail.entity.MailGuildSettings;
import de.chojo.lyna.feature.mail.entity.MailLink;
import de.chojo.lyna.feature.mail.repository.MailGuildSettingsRepository;
import de.chojo.lyna.feature.mail.repository.MailingRepository;
import de.chojo.lyna.feature.product.entity.ProductLinks;
import de.chojo.lyna.feature.product.repository.ProductLinksRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a guild's purchase mails share, and the per-product addresses they fill in.
 */
class MailSettingsRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 5101L;
    private static final long OTHER_GUILD = 5102L;

    private final MailGuildSettingsRepository guildMail = new MailGuildSettingsRepository();
    private final MailingRepository mailings = new MailingRepository();
    private final ProductLinksRepository productLinks = new ProductLinksRepository();
    private final GuildSettingsRepository guildSettings = new GuildSettingsRepository();

    private int withMail;
    private int withoutMail;
    private int mailingId;

    @BeforeEach
    void seed() throws SQLException {
        clear("mail_products", "mail_guild_settings", "license_settings", "product");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            withMail = insert(
                    statement,
                    "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, 'Widget', 5) RETURNING id"
                            .formatted(schemaName, GUILD));
            withoutMail = insert(
                    statement,
                    "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, 'Gadget', 5) RETURNING id"
                            .formatted(schemaName, GUILD));
            insert(
                    statement,
                    "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, 'Elsewhere', 5) RETURNING id"
                            .formatted(schemaName, OTHER_GUILD));
            mailingId = insert(statement, """
                    INSERT INTO %s.mail_products (product_id, name, mail_text, blocks)
                    VALUES (%d, 'Widget', '<p>old</p>', '[{"type":"divider"}]') RETURNING id
                    """.formatted(schemaName, withMail));
        }
    }

    private static int insert(Statement statement, String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static String one(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    @Test
    @DisplayName("A guild that never saved anything reads as empty, not as missing")
    void unsavedGuildIsEmpty() {
        assertEquals(MailGuildSettings.empty(GUILD), guildMail.get(GUILD));
    }

    @Test
    @DisplayName("The default mail is saved, and going back to the shipped one empties it again")
    void defaultBlocksAreSavedAndReset() {
        guildMail.blocks(GUILD, "[{\"type\":\"divider\"}]");
        assertTrue(guildMail.get(GUILD).blocks().contains("divider"));

        guildMail.blocks(GUILD, null);
        assertNull(guildMail.get(GUILD).blocks());
    }

    @Test
    @DisplayName("The Discord invite and the links are saved without touching the default mail")
    void contactIsSavedBesideTheBlocks() {
        guildMail.blocks(GUILD, "[{\"type\":\"divider\"}]");
        List<MailLink> links = List.of(
                new MailLink("GitHub", "https://example.invalid/gh"),
                new MailLink("Patreon", "https://example.invalid/p"));

        guildMail.contact(GUILD, "https://example.invalid/discord", links);

        MailGuildSettings settings = guildMail.get(GUILD);
        assertEquals("https://example.invalid/discord", settings.discordUrl());
        assertEquals(links, settings.links());
        assertTrue(settings.blocks().contains("divider"), "the default mail is still there");

        guildMail.contact(GUILD, null, null);
        assertNull(guildMail.get(GUILD).discordUrl());
        assertEquals(List.of(), guildMail.get(GUILD).links());
    }

    @Test
    @DisplayName("Emptying a mail takes both its blocks and its old HTML")
    void clearBodyEmptiesBoth() throws SQLException {
        assertTrue(mailings.clearBody(mailingId));

        assertNull(one("SELECT blocks FROM %s.mail_products WHERE id = %d".formatted(schemaName, mailingId)));
        assertEquals("", one("SELECT mail_text FROM %s.mail_products WHERE id = %d".formatted(schemaName, mailingId)));
    }

    @Test
    @DisplayName("Every product of the guild without a mail gets an empty one named after it, and only those")
    void missingMailsAreCreated() throws SQLException {
        assertEquals(1, mailings.createMissing(GUILD));

        assertEquals(
                "Gadget",
                one("SELECT name FROM %s.mail_products WHERE product_id = %d".formatted(schemaName, withoutMail)));
        assertEquals(
                "1",
                one("SELECT count(*) FROM %s.mail_products WHERE product_id = %d".formatted(schemaName, withMail)));
        assertEquals(0, mailings.createMissing(GUILD), "running it again makes nothing");
        assertEquals(
                "2",
                one("SELECT count(*) FROM %s.mail_products".formatted(schemaName)),
                "the other guild is untouched");
    }

    @Test
    @DisplayName("A product's wiki and review addresses are saved and read back")
    void productLinksRoundTrip() {
        assertEquals(ProductLinks.NONE, productLinks.get(withMail));

        productLinks.set(withMail, new ProductLinks("https://example.invalid/wiki", "https://example.invalid/review"));

        assertEquals(
                new ProductLinks("https://example.invalid/wiki", "https://example.invalid/review"),
                productLinks.get(withMail));
        assertEquals(ProductLinks.NONE, productLinks.get(-1), "a product that does not exist has none");
        assertEquals(
                "https://example.invalid/wiki",
                kioskProducts.byId(withMail).orElseThrow().wikiUrl(),
                "and the product page reads the wiki too");
    }

    @Test
    @DisplayName("How many people a licence may be shared with, nobody when the guild never said")
    void sharesAreRead() {
        assertEquals(0, guildSettings.shares(GUILD));

        guildSettings.setShares(GUILD, 2);

        assertEquals(2, guildSettings.shares(GUILD));
    }
}
