/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.TestConf;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.guild.LicenseGuild;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.entity.LicenseSource;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.purchase.service.PurchaseService;
import de.chojo.lyna.mail.Mail;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.repository.RepositoryTestBase;
import de.chojo.nexus.NexusRest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A purchase, from the payment to the mail.
 *
 * <p>The same steps run for a shop order, for a receipt parsed out of the mailbox, and for an
 * operator issuing one by hand. They used to be written out three times, in a webhook, a mail
 * listener and a slash command, and none of the three could be reached from a test. Here they can.
 */
class PurchaseServiceTest extends RepositoryTestBase {
    private static final long GUILD = 9001L;
    private static final String BUYER = "buyer@example.invalid";

    private PurchaseService purchases;
    private List<Mail> sent;
    private Product product;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "license_invite",
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "mail_products",
                "product",
                "account_email",
                "account_identity",
                "account");

        Conf configuration = TestConf.defaults();
        Guilds guilds = new Guilds(Mockito.mock(NexusRest.class), configuration, accountLinks);
        LicenseGuild licenseGuild = guilds.guild(GUILD);

        int productId;
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("""
                    INSERT INTO %s.product (guild_id, name, url, role)
                    VALUES (%d, 'Widget', 'https://example.invalid/widget', 5) RETURNING id
                    """.formatted(schemaName, GUILD))) {
                rows.next();
                productId = rows.getInt(1);
            }
            statement.execute("""
                    INSERT INTO %s.mail_products (product_id, name, mail_text)
                    VALUES (%d, 'Widget mail', '<p>Your key: {{ key }}</p>')
                    """.formatted(schemaName, productId));
        }
        product = licenseGuild.products().byId(productId).orElseThrow();

        sent = new java.util.ArrayList<>();
        MailingService mailing = Mockito.mock(MailingService.class);
        Mockito.when(mailing.renderer())
                .thenReturn(new de.chojo.lyna.mail.MailTemplateRenderer("Lyna", "https://x.invalid"));
        Mockito.doAnswer(invocation -> sent.add(invocation.getArgument(0)))
                .when(mailing)
                .sendMail(Mockito.any());

        purchases = new PurchaseService(licenseService, purchaseCollection, mailing);
    }

    @Test
    @DisplayName("A purchase mints a licence, grants the stable release and mails the key")
    void aPurchaseIsIssued() {
        Optional<License> license = purchases.issue(product, BUYER, "Ada Lovelace", LicenseSource.KOFI);

        assertTrue(license.isPresent());
        assertEquals(BUYER, license.get().userIdentifier());
        assertFalse(licenseService.access(license.get()).isEmpty(), "the stable release is granted");
        assertEquals(1, sent.size(), "the buyer is told");
        assertTrue(sent.get(0).text().contains(license.get().key()), "and the key is in it");
    }

    @Test
    @DisplayName("Somebody who proved the address already finds the licence in their account")
    void aProvedAddressCollectsItAtOnce() {
        var account = accountService.register(BUYER, "hash");
        accountEmailService.confirm(account.id(), BUYER);

        Optional<License> license = purchases.issue(product, BUYER, "Ada Lovelace", LicenseSource.KOFI);

        assertTrue(license.isPresent());
        assertEquals(
                List.of(license.get().id()),
                accountLicenses.owned(account.id()).stream()
                        .map(de.chojo.lyna.feature.account.entity.AccountLicense::id)
                        .toList());
        assertTrue(sent.get(0).text().contains("Open your licences"), "and the mail says so");
    }

    @Test
    @DisplayName("Somebody who only claimed the address is not told it is in their account")
    void aClaimedAddressIsNotEnough() {
        var account = accountService.register("signed-up@example.invalid", "hash");
        accountEmails.add(account.id(), BUYER);

        purchases.issue(product, BUYER, "Ada Lovelace", LicenseSource.KOFI);

        assertTrue(accountLicenses.owned(account.id()).isEmpty(), "claiming took nothing");
        assertTrue(sent.get(0).text().contains("Create an account"), "and the mail invites them instead");
    }

    @Test
    @DisplayName("A product with no mail to send issues nothing, because the key would reach nobody")
    void withoutAMailTemplateNothingIsIssued() throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM %s.mail_products".formatted(schemaName));
        }
        Product bare = product.products().byId(product.id()).orElseThrow();

        assertTrue(
                purchases.issue(bare, BUYER, "Ada Lovelace", LicenseSource.KOFI).isEmpty());
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("Where the purchase came from is recorded on the licence")
    void theSourceIsKept() throws SQLException {
        purchases.issue(product, BUYER, "Ada Lovelace", LicenseSource.MAIL);

        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT source FROM %s.license WHERE LOWER(user_identifier) = '%s'"
                        .formatted(schemaName, BUYER))) {
            rows.next();
            assertEquals("MAIL", rows.getString(1));
        }
    }
}
