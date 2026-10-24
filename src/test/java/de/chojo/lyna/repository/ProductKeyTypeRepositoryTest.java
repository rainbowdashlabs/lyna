/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.updater.entity.ProductKeyType;
import de.chojo.lyna.feature.updater.entity.ProductKeyType.Draft;
import de.chojo.lyna.feature.updater.repository.ProductKeyTypeRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenQuotaRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The key types products hand out: written only by the guild owning the product, named uniquely
 * within it, found by their gateway secret, and ended together with their keys and their product.
 */
class ProductKeyTypeRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 5301L;

    private final ProductKeyTypeRepository keyTypes = new ProductKeyTypeRepository();
    private final UpdateTokenRepository tokens = new UpdateTokenRepository();
    private final UpdateTokenQuotaRepository quota = new UpdateTokenQuotaRepository();
    private int account;
    private int product;
    private int sibling;
    private int elsewhere;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "update_token_request",
                "update_token_quota",
                "update_token_retired",
                "update_token",
                "product_key_type",
                "product",
                "account_identity",
                "account");
        account = accountService.register("keytypes@example.invalid", "hash").id();
        product = product("Companies", GUILD);
        sibling = product("Chatty", GUILD);
        elsewhere = product("Elsewhere", GUILD + 1);
    }

    private int product(String name, long guild) throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, '%s', 5) RETURNING id"
                                .formatted(schemaName, guild, name))) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static Draft draft(String slug, String name, boolean enabled) {
        return new Draft(slug, name, "/companyadmin dashboard key {key}", enabled, 7, 30, 10, 1);
    }

    private ProductKeyType create(int productId, String slug, String name, boolean enabled) {
        return keyTypes.create(GUILD, productId, draft(slug, name, enabled), "lyg_" + slug + productId)
                .orElseThrow();
    }

    @Test
    @DisplayName("A created key type carries what was set, and is found by its id and its secret")
    void createAndFind() {
        Draft draft = new Draft("dashboard", "Dashboard key", null, false, 3, 14, 5, 2);

        ProductKeyType created =
                keyTypes.create(GUILD, product, draft, "lyg_secret").orElseThrow();

        assertEquals(product, created.productId());
        assertEquals("dashboard", created.slug());
        assertEquals("Dashboard key", created.name());
        assertEquals(null, created.command());
        assertFalse(created.enabled());
        assertEquals(3, created.refreshDays());
        assertEquals(14, created.expiryDays());
        assertEquals(5, created.allowance());
        assertEquals(2, created.trialAllowance());
        assertEquals("lyg_secret", created.gatewaySecret());
        assertEquals(Optional.of(created), keyTypes.byId(created.id()));
        assertEquals(Optional.of(created), keyTypes.bySecret("lyg_secret"));
        assertTrue(keyTypes.bySecret("lyg_nobody").isEmpty());
        assertTrue(keyTypes.byId(created.id() + 1000).isEmpty());
    }

    @Test
    @DisplayName("A guild makes key types only for its own products")
    void createLimitedToGuild() {
        assertTrue(keyTypes.create(GUILD, elsewhere, draft("dashboard", "Dashboard key", true), "lyg_one")
                .isEmpty());
        assertTrue(keyTypes.create(GUILD + 1, product, draft("dashboard", "Dashboard key", true), "lyg_two")
                .isEmpty());
        assertEquals(List.of(), keyTypes.forProduct(product));
        assertEquals(List.of(), keyTypes.forProduct(elsewhere));
    }

    @Test
    @DisplayName("A slug is unique within its product, and free to use on another")
    void slugUniquePerProduct() {
        create(product, "dashboard", "Dashboard key", true);

        assertTrue(keyTypes.create(GUILD, product, draft("dashboard", "Again", true), "lyg_again")
                .isEmpty());
        assertTrue(keyTypes.create(GUILD, sibling, draft("dashboard", "Dashboard key", true), "lyg_sibling")
                .isPresent());
        assertEquals(1, keyTypes.forProduct(product).size());
    }

    @Test
    @DisplayName("A product lists its key types by name; enabled ones are found across products")
    void forProductAndEnabled() {
        ProductKeyType proxy = create(product, "proxy", "Proxy key", false);
        ProductKeyType dashboard = create(product, "dashboard", "Dashboard key", true);
        ProductKeyType chatty = create(sibling, "dashboard", "Chatty key", true);

        assertEquals(List.of(dashboard, proxy), keyTypes.forProduct(product));
        assertEquals(List.of(chatty), keyTypes.forProduct(sibling));
        assertEquals(List.of(dashboard, chatty), keyTypes.enabledFor(List.of(product, sibling)));
        assertEquals(List.of(chatty), keyTypes.enabledFor(List.of(sibling, elsewhere)));
        assertEquals(List.of(), keyTypes.enabledFor(List.of()));
    }

    @Test
    @DisplayName("Updating changes everything an admin sets, but only on the guild's products and to a free slug")
    void update() {
        ProductKeyType dashboard = create(product, "dashboard", "Dashboard key", true);
        create(product, "proxy", "Proxy key", true);
        Draft changed = new Draft("panel", "Panel key", "/panel {key}", false, 2, 9, 4, 0);

        assertTrue(keyTypes.update(GUILD + 1, dashboard.id(), changed).isEmpty(), "not another guild's");
        assertTrue(
                keyTypes.update(GUILD, dashboard.id(), draft("proxy", "Taken", true))
                        .isEmpty(),
                "the slug is taken");
        assertTrue(keyTypes.update(GUILD, dashboard.id() + 1000, changed).isEmpty());
        assertEquals(Optional.of(dashboard), keyTypes.byId(dashboard.id()));

        ProductKeyType updated = keyTypes.update(GUILD, dashboard.id(), changed).orElseThrow();

        assertEquals(
                new ProductKeyType(
                        dashboard.id(),
                        product,
                        "panel",
                        "Panel key",
                        "/panel {key}",
                        false,
                        2,
                        9,
                        4,
                        0,
                        dashboard.gatewaySecret()),
                updated);
        assertTrue(
                keyTypes.update(GUILD, dashboard.id(), draft("panel", "Same slug", true))
                        .isPresent(),
                "keeping its own slug is no conflict");
    }

    @Test
    @DisplayName("A new secret replaces the old one at once, and only the guild's admin can set one")
    void replaceSecret() {
        ProductKeyType dashboard = create(product, "dashboard", "Dashboard key", true);

        assertTrue(
                keyTypes.replaceSecret(GUILD + 1, dashboard.id(), "lyg_stolen").isEmpty());
        ProductKeyType replaced =
                keyTypes.replaceSecret(GUILD, dashboard.id(), "lyg_fresh").orElseThrow();

        assertEquals("lyg_fresh", replaced.gatewaySecret());
        assertNotEquals(dashboard.gatewaySecret(), replaced.gatewaySecret());
        assertTrue(keyTypes.bySecret(dashboard.gatewaySecret()).isEmpty());
        assertEquals(Optional.of(replaced), keyTypes.bySecret("lyg_fresh"));
        assertTrue(keyTypes.bySecret("lyg_stolen").isEmpty());
    }

    @Test
    @DisplayName("Deleting a key type ends its keys, allowances and requests, and leaves every other key")
    void deleteEndsKeys() {
        ProductKeyType dashboard = create(product, "dashboard", "Dashboard key", true);
        ProductKeyType proxy = create(product, "proxy", "Proxy key", true);
        var key = tokens.create(account, product, dashboard.id(), "lobby", "lyd_one");
        var other = tokens.create(account, product, proxy.id(), "lobby", "lyd_two");
        var upgrade = tokens.create(account, product, null, "lobby", "lyu_one");
        assertTrue(quota.setGranted(GUILD, account, product, dashboard.id(), 3));
        assertTrue(quota.request(account, product, dashboard.id(), 1, "more").isPresent());

        assertFalse(keyTypes.delete(GUILD + 1, dashboard.id()), "not another guild's");
        assertTrue(tokens.byId(key.id()).isPresent());

        assertTrue(keyTypes.delete(GUILD, dashboard.id()));

        assertFalse(keyTypes.delete(GUILD, dashboard.id()), "already gone");
        assertTrue(keyTypes.byId(dashboard.id()).isEmpty());
        assertTrue(tokens.byId(key.id()).isEmpty());
        assertEquals(0, quota.granted(account, product, dashboard.id()));
        assertEquals(List.of(), quota.requestsOf(account));
        assertTrue(tokens.byId(other.id()).isPresent());
        assertTrue(tokens.byId(upgrade.id()).isPresent());
    }

    @Test
    @DisplayName("Deleting a product deletes its key types")
    void deleteProduct() throws SQLException {
        ProductKeyType dashboard = create(product, "dashboard", "Dashboard key", true);
        ProductKeyType chatty = create(sibling, "dashboard", "Chatty key", true);

        execute("DELETE FROM %%s.product WHERE id = %d".formatted(product));

        assertTrue(keyTypes.byId(dashboard.id()).isEmpty());
        assertTrue(keyTypes.bySecret(dashboard.gatewaySecret()).isEmpty());
        assertEquals(Optional.of(chatty), keyTypes.byId(chatty.id()));
    }
}
