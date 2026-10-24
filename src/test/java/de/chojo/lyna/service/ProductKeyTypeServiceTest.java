/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.updater.entity.ProductKeyType;
import de.chojo.lyna.feature.updater.entity.ProductKeyType.Draft;
import de.chojo.lyna.feature.updater.repository.ProductKeyTypeRepository;
import de.chojo.lyna.feature.updater.service.ProductKeyTypeService;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Refusal;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Key types as a guild's admin sets them up: drafts out of bounds refused, texts cleaned, a gateway
 * secret of their own for each, and nothing reachable on another guild's products.
 */
class ProductKeyTypeServiceTest extends RepositoryTestBase {
    private static final long GUILD = 5401L;
    private static final String SECRET = "^lyg_[A-Za-z0-9_-]{43}$";

    private final ProductKeyTypeService service = new ProductKeyTypeService(new ProductKeyTypeRepository());
    private int product;
    private int elsewhere;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "update_token_request",
                "update_token_quota",
                "update_token_retired",
                "update_token",
                "product_key_type",
                "product");
        product = product("Companies", GUILD);
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

    private static Draft draft(String slug) {
        return new Draft(slug, "Dashboard key", "/companyadmin dashboard key {key}", true, 7, 30, 10, 1);
    }

    private ProductKeyType created(String slug) {
        return service.create(GUILD, product, draft(slug)).value();
    }

    private static List<Draft> invalidDrafts() {
        String longName = "x".repeat(Draft.MAX_NAME_LENGTH + 1);
        String longCommand = "x".repeat(Draft.MAX_COMMAND_LENGTH + 1);
        return List.of(
                new Draft(null, "Key", null, true, 7, 30, 10, 1),
                new Draft("Dashboard", "Key", null, true, 7, 30, 10, 1),
                new Draft("-dashboard", "Key", null, true, 7, 30, 10, 1),
                new Draft("d".repeat(33), "Key", null, true, 7, 30, 10, 1),
                new Draft("dashboard", null, null, true, 7, 30, 10, 1),
                new Draft("dashboard", " ", null, true, 7, 30, 10, 1),
                new Draft("dashboard", longName, null, true, 7, 30, 10, 1),
                new Draft("dashboard", "Key", longCommand, true, 7, 30, 10, 1),
                new Draft("dashboard", "Key", null, true, 0, 30, 10, 1),
                new Draft("dashboard", "Key", null, true, Draft.MAX_DAYS + 1, 30, 10, 1),
                new Draft("dashboard", "Key", null, true, 7, 0, 10, 1),
                new Draft("dashboard", "Key", null, true, 7, Draft.MAX_DAYS + 1, 10, 1),
                new Draft("dashboard", "Key", null, true, 7, 30, -1, 1),
                new Draft("dashboard", "Key", null, true, 7, 30, Draft.MAX_ALLOWANCE + 1, 1),
                new Draft("dashboard", "Key", null, true, 7, 30, 10, -1),
                new Draft("dashboard", "Key", null, true, 7, 30, 10, Draft.MAX_ALLOWANCE + 1));
    }

    @Test
    @DisplayName("A draft out of bounds is refused, for a new key type and for a change alike")
    void invalidDraftsRefused() {
        ProductKeyType existing = created("dashboard");

        assertEquals(
                Refusal.INVALID_REQUEST, service.create(GUILD, product, null).refusal());
        assertEquals(
                Refusal.INVALID_REQUEST,
                service.update(GUILD, existing.id(), null).refusal());
        for (Draft draft : invalidDrafts()) {
            assertFalse(draft.valid(), draft.toString());
            assertEquals(
                    Refusal.INVALID_REQUEST,
                    service.create(GUILD, product, draft).refusal(),
                    draft.toString());
            assertEquals(
                    Refusal.INVALID_REQUEST,
                    service.update(GUILD, existing.id(), draft).refusal(),
                    draft.toString());
        }
        assertEquals(List.of(existing), service.forProduct(product));
    }

    @Test
    @DisplayName("The bounds themselves are allowed")
    void boundsAllowed() {
        Draft widest = new Draft(
                "a" + "-".repeat(31),
                "x".repeat(Draft.MAX_NAME_LENGTH),
                "x".repeat(Draft.MAX_COMMAND_LENGTH),
                false,
                Draft.MAX_DAYS,
                Draft.MAX_DAYS,
                Draft.MAX_ALLOWANCE,
                Draft.MAX_ALLOWANCE);
        Draft narrowest = new Draft("0", "x", null, true, 1, 1, 0, 0);

        assertTrue(service.create(GUILD, product, widest).ok());
        assertTrue(service.create(GUILD, product, narrowest).ok());
    }

    @Test
    @DisplayName("Texts are stored trimmed, and an empty command as none")
    void cleaned() {
        ProductKeyType created = service.create(
                        GUILD, product, new Draft("dashboard", "  Dashboard key  ", "   ", true, 7, 30, 10, 1))
                .value();

        assertEquals("Dashboard key", created.name());
        assertNull(created.command());

        ProductKeyType updated = service.update(
                        GUILD, created.id(), new Draft("dashboard", " Panel ", "  /panel {key}  ", true, 7, 30, 10, 1))
                .value();

        assertEquals("Panel", updated.name());
        assertEquals("/panel {key}", updated.command());
        assertEquals(created.gatewaySecret(), updated.gatewaySecret(), "a change keeps the secret");
    }

    @Test
    @DisplayName("Every key type gets a secret of its own, and a new one replaces it")
    void secrets() {
        ProductKeyType dashboard = created("dashboard");
        ProductKeyType proxy = created("proxy");

        assertTrue(dashboard.gatewaySecret().matches(SECRET), dashboard.gatewaySecret());
        assertTrue(dashboard.gatewaySecret().startsWith(ProductKeyTypeService.SECRET_PREFIX));
        assertNotEquals(dashboard.gatewaySecret(), proxy.gatewaySecret());

        ProductKeyType replaced = service.replaceSecret(GUILD, dashboard.id()).value();

        assertTrue(replaced.gatewaySecret().matches(SECRET));
        assertNotEquals(dashboard.gatewaySecret(), replaced.gatewaySecret());
        assertEquals(dashboard.id(), replaced.id());
    }

    @Test
    @DisplayName("A slug the product already has is refused as pending")
    void slugConflict() {
        created("dashboard");

        assertEquals(
                Refusal.PENDING,
                service.create(GUILD, product, draft("dashboard")).refusal());
        ProductKeyType proxy = created("proxy");
        assertEquals(
                Refusal.NOT_FOUND,
                service.update(GUILD, proxy.id(), draft("dashboard")).refusal());
    }

    @Test
    @DisplayName("Another guild's product and key types are out of reach")
    void otherGuild() {
        ProductKeyType dashboard = created("dashboard");

        assertEquals(
                Refusal.PENDING,
                service.create(GUILD, elsewhere, draft("dashboard")).refusal());
        assertEquals(
                Refusal.NOT_FOUND,
                service.update(GUILD + 1, dashboard.id(), draft("panel")).refusal());
        assertEquals(
                Refusal.NOT_FOUND,
                service.replaceSecret(GUILD + 1, dashboard.id()).refusal());
        assertFalse(service.delete(GUILD + 1, dashboard.id()));
        assertEquals(List.of(dashboard), service.forProduct(product));

        assertTrue(service.delete(GUILD, dashboard.id()));
        assertEquals(List.of(), service.forProduct(product));
        assertEquals(
                Refusal.NOT_FOUND, service.replaceSecret(GUILD, dashboard.id()).refusal());
    }
}
