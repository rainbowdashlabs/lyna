/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.kiosk.entity.KioskProduct;
import de.chojo.lyna.feature.product.repository.ProductFamilyRepository;
import de.chojo.lyna.feature.product.service.ProductFamilyService;
import de.chojo.lyna.feature.product.service.ProductFamilyService.ParentChange;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which products may be add-ons of which, and what a product's family is.
 */
class ProductFamilyServiceTest extends RepositoryTestBase {
    private static final long GUILD = 4301L;
    private static final long OTHER_GUILD = 4302L;

    private final ProductFamilyService service = new ProductFamilyService(new ProductFamilyRepository());

    private int base;
    private int first;
    private int second;
    private int elsewhere;

    @BeforeEach
    void seed() throws SQLException {
        clear("product");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            base = insert(statement, GUILD, "Base");
            first = insert(statement, GUILD, "First");
            second = insert(statement, GUILD, "Second");
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

    @Test
    @DisplayName("A product becomes an add-on and stands on its own again")
    void setAndClear() {
        assertEquals(ParentChange.OK, service.parent(first, base));
        assertEquals(Map.of(first, base), service.parents());

        assertEquals(ParentChange.OK, service.parent(first, null));
        assertTrue(service.parents().isEmpty());
    }

    @Test
    @DisplayName("Neither the product itself, nor a missing product, nor another guild's product is a parent")
    void refusesStrangeParents() {
        assertEquals(ParentChange.NO_SUCH_PRODUCT, service.parent(-1, base));
        assertEquals(ParentChange.SELF, service.parent(first, first));
        assertEquals(ParentChange.NO_SUCH_PARENT, service.parent(first, -1));
        assertEquals(ParentChange.NO_SUCH_PARENT, service.parent(first, elsewhere));
        assertTrue(service.parents().isEmpty());
    }

    @Test
    @DisplayName("Checking a change answers as the change would and changes nothing")
    void checkChangesNothing() {
        assertEquals(ParentChange.OK, service.check(first, base));
        assertEquals(ParentChange.OK, service.check(first, null));
        assertEquals(ParentChange.NO_SUCH_PRODUCT, service.check(-1, null));
        assertTrue(service.parents().isEmpty());
    }

    @Test
    @DisplayName("A family is one level: an add-on has no add-ons and a parent is no add-on")
    void oneLevel() {
        assertEquals(ParentChange.OK, service.parent(first, base));

        assertEquals(ParentChange.PARENT_IS_ADDON, service.parent(second, first));
        assertEquals(ParentChange.HAS_ADDONS, service.parent(base, second));
        assertEquals(ParentChange.OK, service.parent(base, null), "a parent may always stand on its own");
        assertEquals(Map.of(first, base), service.parents());
    }

    @Test
    @DisplayName("An add-on's family is its parent and its siblings; a parent's is its add-ons")
    void family() {
        var parent = product(1, null);
        var addonA = product(2, 1);
        var addonB = product(3, 1);
        var loner = product(4, null);
        var catalogue = List.of(parent, addonA, addonB, loner);

        var ofAddon = ProductFamilyService.family(addonA, catalogue);
        assertEquals(parent, ofAddon.parent());
        assertTrue(ofAddon.addons().isEmpty());
        assertEquals(List.of(addonB), ofAddon.similar());

        var ofParent = ProductFamilyService.family(parent, catalogue);
        assertNull(ofParent.parent());
        assertEquals(List.of(addonA, addonB), ofParent.addons());
        assertTrue(ofParent.similar().isEmpty());

        var ofLoner = ProductFamilyService.family(loner, catalogue);
        assertNull(ofLoner.parent());
        assertTrue(ofLoner.addons().isEmpty());
        assertTrue(ofLoner.similar().isEmpty());
    }

    @Test
    @DisplayName("An add-on whose parent is not in the catalogue has no parent to show but keeps its siblings")
    void parentMissingFromCatalogue() {
        var addonA = product(2, 1);
        var addonB = product(3, 1);

        var family = ProductFamilyService.family(addonA, List.of(addonA, addonB));
        assertNull(family.parent());
        assertEquals(List.of(addonB), family.similar());
    }

    private static KioskProduct product(int id, Integer parentId) {
        return new KioskProduct(id, GUILD, "P" + id, null, null, false, null, null, false, false, null, parentId);
    }
}
