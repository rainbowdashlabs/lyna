/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.TestConf;
import de.chojo.lyna.configuration.elements.BuiltByBit;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitListing;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitLogEntry;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitOutcome;
import de.chojo.lyna.feature.builtbybit.repository.BuiltByBitLogRepository;
import de.chojo.lyna.feature.builtbybit.repository.BuiltByBitProductRepository;
import de.chojo.lyna.feature.builtbybit.service.BuiltByBitApi;
import de.chojo.lyna.feature.builtbybit.service.BuiltByBitApi.Verdict;
import de.chojo.lyna.feature.builtbybit.service.BuiltByBitService;
import de.chojo.lyna.feature.builtbybit.service.BuiltByBitService.KeyAnswer;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.guild.repository.GuildSettingsRepository;
import de.chojo.lyna.feature.mail.repository.MailGuildSettingsRepository;
import de.chojo.lyna.feature.mail.service.PurchaseMailService;
import de.chojo.lyna.feature.product.repository.ProductLinksRepository;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.feature.purchase.service.PurchaseService;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.repository.RepositoryTestBase;
import de.chojo.nexus.NexusRest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BuiltByBit reporting a purchase and asking for the key to write into a download, from the call to
 * the licence.
 */
class BuiltByBitServiceTest extends RepositoryTestBase {
    private static final long GUILD = 9101L;
    private static final long MEMBER = 4242L;
    private static final long RESOURCE = 1234L;
    private static final long ADDON = 77L;

    private final BuiltByBit config = new BuiltByBit();
    private final BuiltByBitProductRepository listings = new BuiltByBitProductRepository();
    private final BuiltByBitLogRepository calls = new BuiltByBitLogRepository();
    private final AtomicReference<Verdict> verdict = new AtomicReference<>(Verdict.UNCHECKED);
    private BuiltByBitService service;
    private int product;
    private int addonProduct;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "builtbybit_purchase_log",
                "builtbybit_product",
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "product");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            product = insert(statement, "Widget");
            addonProduct = insert(statement, "Widget Addon");
        }
        listings.assign(product, BuiltByBitListing.resource(RESOURCE));
        listings.assign(addonProduct, new BuiltByBitListing(RESOURCE, ADDON));

        Conf configuration = TestConf.defaults();
        Guilds guilds = new Guilds(Mockito.mock(NexusRest.class), configuration, accountLinks);
        PurchaseService purchases = new PurchaseService(
                licenseService,
                purchaseCollection,
                Mockito.mock(MailingService.class),
                new PurchaseMailService(
                        new MailGuildSettingsRepository(),
                        new ProductLinksRepository(),
                        productIcons,
                        kioskProducts,
                        new GuildSettingsRepository(),
                        configuration.main().links()));
        BuiltByBitApi api = (member, listing) -> verdict.get();
        service = new BuiltByBitService(config, listings, calls, new ProductLookup(guilds), purchases, api);
    }

    private int insert(Statement statement, String name) throws SQLException {
        String sql = "INSERT INTO %s.product (guild_id, name, role, free) VALUES (%d, '%s', 0, FALSE) RETURNING id"
                .formatted(schemaName, GUILD, name);
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static String purchase(long resource, String addon, String bundle) {
        return """
                { "username": "Ada", "user_id": "%d", "user_url": "https://builtbybit.com/members/%d/",
                  "resource_title": "Widget", "resource_id": "%d", "resource_url": "",
                  "addon_id": "%s", "addon_title": "", "bundle_id": "%s", "bundle_title": "",
                  "renewal": "", "list_price": "10.00", "final_price": "10.00", "purchase_date": "1700000000" }
                """.formatted(MEMBER, MEMBER, resource, addon, bundle);
    }

    private static Map<String, String> keyForm(long resource) {
        Map<String, String> form = new HashMap<>();
        form.put("builtbybit", "true");
        form.put("user_id", String.valueOf(MEMBER));
        form.put("resource_id", String.valueOf(resource));
        form.put("version_id", "9");
        form.put("version_number", "1.2.3");
        return form;
    }

    private int licencesOf(int productId) throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT count(*) FROM %s.license WHERE product_id = %d AND source = 'BUILTBYBIT' AND user_identifier = '%d'"
                                .formatted(schemaName, productId, MEMBER))) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private List<String> loggedOutcomes() {
        return calls.forGuild(GUILD, 50).stream()
                .map(BuiltByBitLogEntry::outcome)
                .toList();
    }

    @Test
    @DisplayName("Only the configured secrets are accepted")
    void secretsAreChecked() {
        assertTrue(service.webhookSecretMatches(config.webhookSecret()));
        assertFalse(service.webhookSecretMatches(config.keySecret()));
        assertFalse(service.webhookSecretMatches(null));
        assertTrue(service.keySecretMatches(config.keySecret()));
        assertFalse(service.keySecretMatches("guess"));
        assertFalse(service.keySecretMatches(null));
    }

    @Test
    @DisplayName("A purchase issues a licence for the member with the stable release, and logs it")
    void purchaseIssues() throws SQLException {
        assertEquals(BuiltByBitOutcome.ISSUED, service.receivePurchase(purchase(RESOURCE, "", "")));

        assertEquals(1, licencesOf(product));
        assertEquals(List.of("ISSUED"), loggedOutcomes());
        assertEquals(1, countRows("license_access"));
    }

    @Test
    @DisplayName("An add-on purchase issues the add-on's product, not the resource's")
    void addonPurchaseIssuesAddon() throws SQLException {
        assertEquals(BuiltByBitOutcome.ISSUED, service.receivePurchase(purchase(RESOURCE, String.valueOf(ADDON), "0")));

        assertEquals(1, licencesOf(addonProduct));
        assertEquals(0, licencesOf(product));
    }

    @Test
    @DisplayName("The same purchase reported twice is still one licence")
    void repeatedPurchaseIsOneLicence() throws SQLException {
        service.receivePurchase(purchase(RESOURCE, "", ""));
        service.receivePurchase(purchase(RESOURCE, "", ""));

        assertEquals(1, licencesOf(product));
    }

    @Test
    @DisplayName("A bundle purchase is logged and issues nothing")
    void bundleIssuesNothing() throws SQLException {
        assertEquals(BuiltByBitOutcome.BUNDLE, service.receivePurchase(purchase(RESOURCE, "", "55")));

        assertEquals(0, licencesOf(product));
        assertEquals(List.of("BUNDLE"), loggedOutcomes());
    }

    @Test
    @DisplayName("A purchase of a resource Lyna does not sell is logged and issues nothing")
    void unknownResourceIssuesNothing() {
        assertEquals(BuiltByBitOutcome.UNKNOWN_PRODUCT, service.receivePurchase(purchase(999, "", "")));

        assertEquals(List.of("UNKNOWN_PRODUCT"), loggedOutcomes());
    }

    @Test
    @DisplayName("A body that is not the template is logged as invalid")
    void invalidBodyIsLogged() {
        assertEquals(BuiltByBitOutcome.INVALID, service.receivePurchase("not json"));
        assertEquals(BuiltByBitOutcome.INVALID, service.receivePurchase("[]"));
        assertEquals(
                BuiltByBitOutcome.INVALID, service.receivePurchase("{\"user_id\": \"x\", \"resource_id\": \"1\"}"));

        assertEquals(List.of("INVALID", "INVALID", "INVALID"), loggedOutcomes());
    }

    @Test
    @DisplayName("A key request issues the licence and answers its key")
    void keyRequestAnswersKey() throws SQLException {
        KeyAnswer answer = service.requestKey(keyForm(RESOURCE), "form");

        assertEquals(BuiltByBitOutcome.ISSUED, answer.outcome());
        assertNotNull(answer.key());
        assertEquals(1, licencesOf(product));
    }

    @Test
    @DisplayName("The purchase and every download meet at the same licence and the same key")
    void purchaseAndDownloadsShareTheLicence() throws SQLException {
        String first = service.requestKey(keyForm(RESOURCE), "form").key();
        service.receivePurchase(purchase(RESOURCE, "", ""));
        String second = service.requestKey(keyForm(RESOURCE), "form").key();

        assertEquals(first, second);
        assertEquals(1, licencesOf(product));
    }

    @Test
    @DisplayName("A repeated download does not give back access an operator took away")
    void repeatedDownloadKeepsRevokedAccess() throws SQLException {
        service.requestKey(keyForm(RESOURCE), "form");
        execute("DELETE FROM %s.license_access");

        service.requestKey(keyForm(RESOURCE), "form");

        assertEquals(0, countRows("license_access"));
    }

    @Test
    @DisplayName("A key request for a resource Lyna does not sell answers no key")
    void unknownResourceAnswersNoKey() {
        KeyAnswer answer = service.requestKey(keyForm(999), "form");

        assertEquals(BuiltByBitOutcome.UNKNOWN_PRODUCT, answer.outcome());
        assertNull(answer.key());
    }

    @Test
    @DisplayName("A key request without a member or resource is invalid")
    void incompleteKeyRequestIsInvalid() {
        Map<String, String> form = keyForm(RESOURCE);
        form.remove("user_id");

        assertEquals(BuiltByBitOutcome.INVALID, service.requestKey(form, "form").outcome());
        assertEquals(List.of("INVALID"), loggedOutcomes());
    }

    @Test
    @DisplayName("With the API, a confirmed purchase is issued")
    void confirmedIsIssued() throws SQLException {
        verdict.set(Verdict.CONFIRMED);

        assertEquals(BuiltByBitOutcome.ISSUED, service.receivePurchase(purchase(RESOURCE, "", "")));
        assertEquals(1, licencesOf(product));
    }

    @Test
    @DisplayName("With the API, a denied purchase or download issues nothing")
    void deniedIssuesNothing() throws SQLException {
        verdict.set(Verdict.DENIED);

        assertEquals(BuiltByBitOutcome.DENIED, service.receivePurchase(purchase(RESOURCE, "", "")));
        assertEquals(
                BuiltByBitOutcome.DENIED,
                service.requestKey(keyForm(RESOURCE), "form").outcome());
        assertEquals(0, licencesOf(product));
    }

    @Test
    @DisplayName("With the API unreachable, nothing is issued")
    void unavailableIssuesNothing() throws SQLException {
        verdict.set(Verdict.UNAVAILABLE);

        assertEquals(BuiltByBitOutcome.API_UNAVAILABLE, service.receivePurchase(purchase(RESOURCE, "", "")));
        assertEquals(0, licencesOf(product));
    }
}
