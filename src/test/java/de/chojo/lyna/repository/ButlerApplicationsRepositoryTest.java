/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

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
 * Which product an UpdateButler application id stands for, so deployed plugins asking by it are still
 * answered.
 */
class ButlerApplicationsRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 3201L;

    private int product;
    private int other;

    @BeforeEach
    void seedProducts() throws SQLException {
        clear("butler_application", "product");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            product = insert(statement, "Mapped");
            other = insert(statement, "Other");
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
    @DisplayName("An assigned id finds its product")
    void assignedIdFindsProduct() {
        butlerApplications.assign(product, 7);

        assertEquals(Optional.of(product), butlerApplications.productFor(7));
    }

    @Test
    @DisplayName("An id nobody carries finds nothing")
    void unknownIdFindsNothing() {
        assertTrue(butlerApplications.productFor(7).isEmpty());
    }

    @Test
    @DisplayName("Assigning again replaces the product's id rather than giving it a second one")
    void assigningAgainReplaces() {
        butlerApplications.assign(product, 7);
        butlerApplications.assign(product, 8);

        assertTrue(butlerApplications.productFor(7).isEmpty());
        assertEquals(Map.of(product, 8), butlerApplications.butlerIdsByProduct());
    }

    @Test
    @DisplayName("An id is taken only when a different product carries it")
    void takenOnlyByAnother() {
        butlerApplications.assign(product, 7);

        assertFalse(butlerApplications.takenByAnother(7, product));
        assertTrue(butlerApplications.takenByAnother(7, other));
        assertFalse(butlerApplications.takenByAnother(8, other));
    }

    @Test
    @DisplayName("Two products cannot carry the same id")
    void idIsExclusive() {
        butlerApplications.assign(product, 7);

        assertThrows(RuntimeException.class, () -> butlerApplications.assign(other, 7));
    }

    @Test
    @DisplayName("Clearing takes the id away")
    void clearingRemoves() {
        butlerApplications.assign(product, 7);
        butlerApplications.clear(product);

        assertTrue(butlerApplications.productFor(7).isEmpty());
    }

    @Test
    @DisplayName("A deleted product takes its id with it")
    void deletedProductTakesItsId() throws SQLException {
        butlerApplications.assign(product, 7);

        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM %s.product WHERE id = %d".formatted(schemaName, product));
        }

        assertTrue(butlerApplications.productFor(7).isEmpty());
    }
}
