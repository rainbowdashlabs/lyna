/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.product.repository.ProductFamilyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductFamilyRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 4201L;
    private static final long OTHER_GUILD = 4202L;

    private final ProductFamilyRepository family = new ProductFamilyRepository();
    private final KioskProductRepository kiosk = new KioskProductRepository();

    private int base;
    private int addon;
    private int elsewhere;

    @BeforeEach
    void seed() throws SQLException {
        clear("product");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            base = insert(statement, GUILD, "Base");
            addon = insert(statement, GUILD, "Addon");
            elsewhere = insert(statement, OTHER_GUILD, "Elsewhere");
        }
    }

    private int insert(Statement statement, long guild, String name) throws SQLException {
        try (var rows =
                statement.executeQuery("INSERT INTO %s.product (guild_id, name, role) VALUES (%d, '%s', 1) RETURNING id"
                        .formatted(schemaName, guild, name))) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private Integer parentInCatalogue(int productId) {
        return kiosk.byId(productId).orElseThrow().parentId();
    }

    @Test
    @DisplayName("A parent is stored, read back on the catalogue and cleared again")
    void parentRoundTrip() {
        family.parent(addon, base);

        assertEquals(base, parentInCatalogue(addon));
        assertEquals(Map.of(addon, base), family.parents());
        var standing = family.standing(base).orElseThrow();
        assertEquals(GUILD, standing.guildId());
        assertNull(standing.parentId());
        assertEquals(1, standing.addons());
        assertEquals(base, family.standing(addon).orElseThrow().parentId());

        family.parent(addon, null);

        assertNull(parentInCatalogue(addon));
        assertTrue(family.parents().isEmpty());
    }

    @Test
    @DisplayName("A product that does not exist has no standing")
    void unknownProduct() {
        assertTrue(family.standing(-1).isEmpty());
    }

    @Test
    @DisplayName("The schema refuses a parent from another guild and the product itself")
    void schemaRefusesStrangers() {
        assertThrows(RuntimeException.class, () -> family.parent(addon, elsewhere));
        assertThrows(RuntimeException.class, () -> family.parent(addon, addon));
        assertNull(parentInCatalogue(addon));
    }

    @Test
    @DisplayName("Deleting a parent leaves its add-ons standing on their own in their guild")
    void deletingParentFreesAddons() throws SQLException {
        family.parent(addon, base);
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM %s.product WHERE id = %d".formatted(schemaName, base));
        }

        var standing = family.standing(addon).orElseThrow();
        assertNull(standing.parentId());
        assertEquals(GUILD, standing.guildId());
    }
}
