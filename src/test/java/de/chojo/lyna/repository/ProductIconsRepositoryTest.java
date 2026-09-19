/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.icon.repository.ProductIconRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the database remembers about a product's icon. The images themselves are files on disk; this
 * row is what says one exists, what kind it is, and when it last changed.
 */
class ProductIconsRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 3101L;

    private int product;
    private int other;

    @BeforeEach
    void seedProducts() throws SQLException {
        clear("product_icon", "product");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            product = insert(statement, "Iconed");
            other = insert(statement, "Bare");
        }
    }

    private int insert(Statement statement, String name) throws SQLException {
        String sql = "INSERT INTO product (guild_id, name, role, free) VALUES (%d, '%s', 1, TRUE) RETURNING id"
                .formatted(GUILD, name);
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    @DisplayName("A recorded icon is found again, as what it is")
    void recordedIconIsFound() {
        productIcons.record(product, "image/png");

        Optional<ProductIconRepository.Icon> icon = productIcons.of(product);

        assertTrue(icon.isPresent());
        assertEquals("image/png", icon.get().mime());
    }

    @Test
    @DisplayName("A product nobody gave an icon has none")
    void aProductWithoutAnIconHasNone() {
        assertTrue(productIcons.of(other).isEmpty());
    }

    @Test
    @DisplayName("Recording again replaces the kind rather than adding a second row")
    void recordingAgainReplaces() {
        productIcons.record(product, "image/png");
        productIcons.record(product, "image/webp");

        assertEquals("image/webp", productIcons.of(product).orElseThrow().mime());
        assertEquals(Set.of(product), productIcons.withIcon());
    }

    @Test
    @DisplayName("Replacing an icon moves the moment a browser caches it by")
    void replacingMovesTheMoment() throws InterruptedException {
        productIcons.record(product, "image/png");
        var first = productIcons.of(product).orElseThrow().updatedAt();
        Thread.sleep(5);
        productIcons.record(product, "image/png");

        assertTrue(productIcons.of(product).orElseThrow().updatedAt().isAfter(first));
    }

    @Test
    @DisplayName("Removing an icon says it removed one, and removing nothing says it did not")
    void removingSaysWhetherThereWasOne() {
        productIcons.record(product, "image/png");

        assertTrue(productIcons.remove(product));
        assertFalse(productIcons.remove(product));
        assertTrue(productIcons.of(product).isEmpty());
    }

    @Test
    @DisplayName("Only the products carrying an icon are named")
    void onlyIconedProductsAreNamed() {
        assertEquals(Set.of(), productIcons.withIcon());

        productIcons.record(product, "image/png");

        assertEquals(Set.of(product), productIcons.withIcon());
    }

    @Test
    @DisplayName("A deleted product takes its icon with it")
    void aDeletedProductTakesItsIcon() throws SQLException {
        productIcons.record(product, "image/png");

        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM %s.product WHERE id = %d".formatted(schemaName, product));
        }

        assertTrue(productIcons.of(product).isEmpty());
    }
}
