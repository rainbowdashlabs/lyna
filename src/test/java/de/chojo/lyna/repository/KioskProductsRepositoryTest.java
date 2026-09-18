/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.data.dao.products.KioskProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KioskProductsRepositoryTest extends RepositoryTestBase {
    private static final long GUILD_A = 3001L;
    private static final long GUILD_B = 3002L;
    private static final long BUYER_DISCORD = 800L;
    private static final long SHAREE_DISCORD = 801L;

    private int buyer;
    private int sharee;

    private int freeProduct;
    private int premiumProduct;
    private int premiumLicense;

    @BeforeEach
    void seedCatalog() throws SQLException {
        clear(
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "kofi_products",
                "product",
                "account_identity",
                "account");
        buyer = de.chojo.lyna.data.access.Accounts.accountIdForDiscord(BUYER_DISCORD);
        sharee = de.chojo.lyna.data.access.Accounts.accountIdForDiscord(SHAREE_DISCORD);

        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            freeProduct = insert(statement, """
                    INSERT INTO product (guild_id, name, url, role, free)
                    VALUES (%d, 'Freebie', 'https://example.invalid/freebie', 1, TRUE) RETURNING id
                    """.formatted(GUILD_A));
            premiumProduct = insert(statement, """
                    INSERT INTO product (guild_id, name, role, free)
                    VALUES (%d, 'Premium', 2, FALSE) RETURNING id
                    """.formatted(GUILD_B));
            statement.execute("INSERT INTO %s.kofi_products (link_code, product_id) VALUES ('abc123', %d)"
                    .formatted(schemaName, premiumProduct));
            premiumLicense = insert(statement, """
                    INSERT INTO license (product_id, user_identifier, key)
                    VALUES (%d, 'buyer@example.invalid', 'KEY-PREMIUM') RETURNING id
                    """.formatted(premiumProduct));
            statement.execute("INSERT INTO %s.user_license (account_id, license_id) VALUES (%d, %d)"
                    .formatted(schemaName, buyer, premiumLicense));
        }
    }

    private static int insert(Statement statement, String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    @DisplayName("The catalogue is every guild's products merged, by name")
    void catalogueIsCrossGuild() {
        List<KioskProduct> all = kioskProducts.all();

        assertEquals(
                List.of("Freebie", "Premium"),
                all.stream().map(KioskProduct::name).toList());
        assertEquals(
                List.of(GUILD_A, GUILD_B),
                all.stream().map(KioskProduct::guildId).toList());
    }

    @Test
    @DisplayName("A premium product is listed too, so it can be offered for sale")
    void premiumProductsAreListed() {
        KioskProduct premium = kioskProducts.all().getLast();

        assertFalse(premium.free());
        assertTrue(kioskProducts.all().getFirst().free());
    }

    @Test
    @DisplayName("A mapped Ko-fi code becomes the address the buy button points at")
    void kofiCodeBecomesAPurchaseUrl() {
        KioskProduct premium = kioskProducts.all().getLast();

        assertEquals("https://ko-fi.com/s/abc123", premium.purchaseUrl());
    }

    @Test
    @DisplayName("A product with several Ko-fi codes is still one entry, offered at one of them")
    void severalKofiCodesStayOneProduct() throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO %s.kofi_products (link_code, product_id) VALUES ('zzz999', %d)"
                    .formatted(schemaName, premiumProduct));
        }

        List<KioskProduct> all = kioskProducts.all();

        assertEquals(
                List.of("Freebie", "Premium"),
                all.stream().map(KioskProduct::name).toList());
        assertEquals("https://ko-fi.com/s/abc123", all.getLast().purchaseUrl());
    }

    @Test
    @DisplayName("A product nobody mapped a Ko-fi code to has nowhere to buy it")
    void productWithoutKofiHasNoPurchaseUrl() {
        assertNull(kioskProducts.all().getFirst().purchaseUrl());
    }

    @Test
    @DisplayName("A product carries its icon once one is set, and drops it again when cleared")
    void iconUrlIsStoredAndCleared() {
        assertNull(kioskProducts.all().getFirst().iconUrl());

        kioskProducts.iconUrl(freeProduct, "https://cdn.example.invalid/freebie.png");
        assertEquals(
                "https://cdn.example.invalid/freebie.png",
                kioskProducts.all().getFirst().iconUrl());

        kioskProducts.iconUrl(freeProduct, "  ");
        assertNull(kioskProducts.all().getFirst().iconUrl());
    }

    @Test
    @DisplayName("Whitespace around an icon address is not part of it")
    void iconUrlIsTrimmed() {
        kioskProducts.iconUrl(freeProduct, "  https://cdn.example.invalid/f.png  ");

        assertEquals(
                "https://cdn.example.invalid/f.png",
                kioskProducts.all().getFirst().iconUrl());
    }

    @Test
    @DisplayName("A license owner is entitled to the product it covers")
    void ownerIsEntitled() {
        assertEquals(java.util.Set.of(premiumProduct), accountLicenses.entitledProductIds(buyer));
    }

    @Test
    @DisplayName("A sharee is entitled to the same product the owner is")
    void shareeIsEntitled() {
        accountLicenses.addSharee(premiumLicense, sharee);

        assertEquals(java.util.Set.of(premiumProduct), accountLicenses.entitledProductIds(sharee));
    }

    @Test
    @DisplayName("Somebody holding no license is entitled to nothing")
    void strangerIsEntitledToNothing() {
        assertTrue(accountLicenses
                .entitledProductIds(de.chojo.lyna.data.access.Accounts.accountIdForDiscord(999L))
                .isEmpty());
    }

    @Test
    @DisplayName("Revoking a share takes the entitlement with it")
    void revokingRemovesEntitlement() {
        accountLicenses.addSharee(premiumLicense, sharee);
        accountLicenses.removeSharee(premiumLicense, sharee);

        assertTrue(accountLicenses.entitledProductIds(sharee).isEmpty());
    }
}
