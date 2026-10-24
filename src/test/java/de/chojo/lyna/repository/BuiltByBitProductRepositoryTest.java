/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitListing;
import de.chojo.lyna.feature.builtbybit.repository.BuiltByBitProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which product a BuiltByBit resource or add-on is sold as.
 */
class BuiltByBitProductRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 3301L;
    private static final BuiltByBitListing RESOURCE = BuiltByBitListing.resource(1234);
    private static final BuiltByBitListing ADDON = new BuiltByBitListing(1234, 77);

    private final BuiltByBitProductRepository listings = new BuiltByBitProductRepository();
    private int product;
    private int other;

    @BeforeEach
    void seedProducts() throws SQLException {
        clear("builtbybit_purchase_log", "builtbybit_product", "product");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            product = insert(statement, "Mapped");
            other = insert(statement, "Other");
        }
    }

    private int insert(Statement statement, String name) throws SQLException {
        String sql = "INSERT INTO product (guild_id, name, role, free) VALUES (%d, '%s', 1, FALSE) RETURNING id"
                .formatted(GUILD, name);
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    @DisplayName("An assigned listing finds its product, and the product its listing")
    void assignedListingFindsProduct() {
        listings.assign(product, RESOURCE);

        assertEquals(Optional.of(product), listings.productFor(RESOURCE));
        assertEquals(Optional.of(RESOURCE), listings.listingOf(product));
    }

    @Test
    @DisplayName("A resource and its add-on are different listings")
    void addonIsItsOwnListing() {
        listings.assign(product, RESOURCE);
        listings.assign(other, ADDON);

        assertEquals(Optional.of(product), listings.productFor(RESOURCE));
        assertEquals(Optional.of(other), listings.productFor(ADDON));
    }

    @Test
    @DisplayName("A listing nobody is sold as finds nothing")
    void unknownListingFindsNothing() {
        assertTrue(listings.productFor(RESOURCE).isEmpty());
        assertTrue(listings.listingOf(product).isEmpty());
    }

    @Test
    @DisplayName("Assigning again replaces the product's listing rather than giving it a second one")
    void assigningAgainReplaces() {
        listings.assign(product, RESOURCE);
        listings.assign(product, ADDON);

        assertTrue(listings.productFor(RESOURCE).isEmpty());
        assertEquals(Map.of(product, ADDON), listings.listingsByProduct());
    }

    @Test
    @DisplayName("A listing is taken only when a different product is sold as it")
    void takenOnlyByAnother() {
        listings.assign(product, RESOURCE);

        assertFalse(listings.takenByAnother(RESOURCE, product));
        assertTrue(listings.takenByAnother(RESOURCE, other));
        assertFalse(listings.takenByAnother(ADDON, other));
    }

    @Test
    @DisplayName("Two products cannot be sold as the same listing")
    void listingIsExclusive() {
        listings.assign(product, RESOURCE);

        assertThrows(RuntimeException.class, () -> listings.assign(other, RESOURCE));
    }

    @Test
    @DisplayName("Clearing takes the listing away")
    void clearingRemoves() {
        listings.assign(product, RESOURCE);
        listings.clear(product);

        assertTrue(listings.productFor(RESOURCE).isEmpty());
    }

    @Test
    @DisplayName("A deleted product takes its listing with it")
    void deletedProductTakesItsListing() throws SQLException {
        listings.assign(product, RESOURCE);

        execute("DELETE FROM %s.product WHERE id = " + product);

        assertTrue(listings.productFor(RESOURCE).isEmpty());
    }
}
