/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.TestConf;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.guild.repository.GuildSettingsRepository;
import de.chojo.lyna.feature.mail.entity.MailLink;
import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.mail.repository.MailGuildSettingsRepository;
import de.chojo.lyna.feature.mail.service.PurchaseMailService;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.entity.ProductLinks;
import de.chojo.lyna.feature.product.repository.ProductLinksRepository;
import de.chojo.lyna.mail.blocks.DefaultPurchaseMail;
import de.chojo.lyna.mail.blocks.MailBlockRenderer;
import de.chojo.lyna.repository.RepositoryTestBase;
import de.chojo.nexus.NexusRest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which mail a product sends, and what its placeholders stand for.
 *
 * <p>The point of the default is that a product nobody wrote a mail for still sends a whole one, so
 * the order in which a mail is chosen is what these tests hold.
 */
class PurchaseMailServiceTest extends RepositoryTestBase {
    private static final long GUILD = 7301L;
    private static final String OWN_BLOCKS = "[{\"type\":\"paragraph\",\"text\":\"own\"}]";
    private static final String GUILD_BLOCKS = "[{\"type\":\"paragraph\",\"text\":\"guild\"}]";

    private final MailGuildSettingsRepository guildMail = new MailGuildSettingsRepository();
    private final ProductLinksRepository productLinks = new ProductLinksRepository();
    private final GuildSettingsRepository guildSettings = new GuildSettingsRepository();

    private PurchaseMailService service;
    private Conf configuration;
    private Product product;

    @BeforeEach
    void seed() throws SQLException {
        clear("product_icon", "mail_products", "mail_guild_settings", "license_settings", "product");
        configuration = TestConf.defaults();
        Guilds guilds = new Guilds(Mockito.mock(NexusRest.class), configuration, accountLinks);
        int productId;
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        INSERT INTO %s.product (guild_id, name, url, role)
                        VALUES (%d, 'Widget', 'https://example.invalid/widget', 5) RETURNING id
                        """.formatted(schemaName, GUILD))) {
            rows.next();
            productId = rows.getInt(1);
        }
        product = guilds.guild(GUILD).products().byId(productId).orElseThrow();
        service = new PurchaseMailService(
                guildMail,
                productLinks,
                productIcons,
                kioskProducts,
                guildSettings,
                configuration.main().links());
    }

    private Mailing mailing(String mailText, String blocks) {
        return new Mailing(1, product, "Widget", mailText, blocks);
    }

    @Test
    @DisplayName("A guild that never wrote a default sends the one the application ships")
    void guildDefaultFallsBackToTheShippedOne() {
        assertEquals(DefaultPurchaseMail.BLOCKS, service.guildDefault(GUILD));
        assertEquals(DefaultPurchaseMail.BLOCKS, service.builtInDefault());

        guildMail.blocks(GUILD, GUILD_BLOCKS);

        assertTrue(service.guildDefault(GUILD).contains("guild"));
    }

    @Test
    @DisplayName("A product's own blocks come first, its old HTML second, the guild's default last")
    void theMailIsChosenInOrder() {
        guildMail.blocks(GUILD, GUILD_BLOCKS);

        assertEquals(OWN_BLOCKS, service.blocksFor(mailing("<p>old</p>", OWN_BLOCKS)));
        assertNull(service.blocksFor(mailing("<p>old</p>", null)), "old HTML is sent as it is");
        assertTrue(service.blocksFor(mailing("", null)).contains("guild"));
        assertTrue(service.blocksFor(mailing("  ", "[]")).contains("guild"), "an emptied editor is no body");
    }

    @Test
    @DisplayName("Every placeholder is filled from the product, the guild and the buyer")
    void valuesComeFromEverywhere() {
        productLinks.set(product.id(), new ProductLinks("https://example.invalid/wiki", null));
        guildMail.contact(
                GUILD,
                "https://example.invalid/discord",
                List.of(new MailLink("GitHub", "https://example.invalid/gh")));
        guildSettings.setShares(GUILD, 2);
        String frontend = configuration.main().links().frontend();

        Map<String, Object> values = service.values(product, "Ada", "KEY-1");

        assertEquals("Ada", values.get("name"));
        assertEquals("KEY-1", values.get("key"));
        assertEquals("Widget", values.get("product"));
        assertEquals("https://example.invalid/widget", values.get("productUrl"));
        assertEquals("https://example.invalid/widget", values.get("downloadUrl"));
        assertEquals(frontend + "/products/" + product.id(), values.get("productPage"));
        assertEquals("https://example.invalid/wiki", values.get("wikiUrl"));
        assertNull(values.get("reviewUrl"));
        assertEquals("https://example.invalid/discord", values.get("discordUrl"));
        assertEquals(2, values.get("shares"));
        assertEquals(
                List.of(new MailLink("GitHub", "https://example.invalid/gh")), values.get(MailBlockRenderer.LINKS));
    }

    @Test
    @DisplayName("The icon is the uploaded one when there is one, else the configured address, else none")
    void iconIsResolved() {
        assertNull(service.values(product, "Ada", "K").get("productIcon"));

        kioskProducts.iconUrl(product.id(), "https://example.invalid/icon.png");
        assertEquals(
                "https://example.invalid/icon.png",
                service.values(product, "Ada", "K").get("productIcon"));

        productIcons.record(product.id(), "image/png");
        assertTrue(service.values(product, "Ada", "K")
                .get("productIcon")
                .toString()
                .endsWith("/api/v1/products/%d/icon".formatted(product.id())));
    }

    @Test
    @DisplayName("A preview fills in the product as it is, with a stand-in buyer")
    void sampleValuesUseTheProduct() {
        Map<String, Object> sample = service.sampleValues(product);

        assertEquals("Ada Lovelace", sample.get("name"));
        assertEquals("https://example.invalid/widget", sample.get("productUrl"));
    }
}
