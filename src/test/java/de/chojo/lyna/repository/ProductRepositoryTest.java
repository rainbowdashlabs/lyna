/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.download.entity.ReleaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a product grants, and who has spent their trial of it.
 */
class ProductRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 4101L;
    private static final long MEMBER = 777L;

    private int productId;
    private int licenseId;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "trial",
                "role_access",
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "product",
                "account_identity",
                "account");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (var rows = statement.executeQuery(
                    "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, 'Widget', 5) RETURNING id"
                            .formatted(schemaName, GUILD))) {
                rows.next();
                productId = rows.getInt(1);
            }
            try (var rows = statement.executeQuery("""
                    INSERT INTO %s.license (product_id, user_identifier, key)
                    VALUES (%d, 'buyer@example.invalid', 'PROD-KEY') RETURNING id
                    """.formatted(schemaName, productId))) {
                rows.next();
                licenseId = rows.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("A trial is unspent until it is spent, and then it stays spent")
    void trialsAreSpentOnce() {
        assertTrue(productRepository.trialUnspent(productId, MEMBER));

        productRepository.spendTrial(productId, MEMBER);
        assertFalse(productRepository.trialUnspent(productId, MEMBER));

        productRepository.spendTrial(productId, MEMBER);
        assertFalse(productRepository.trialUnspent(productId, MEMBER), "asking twice changes nothing");
    }

    @Test
    @DisplayName("Somebody else's spent trial is not yours")
    void trialsArePerMember() {
        productRepository.spendTrial(productId, MEMBER);
        assertTrue(productRepository.trialUnspent(productId, 888L));
    }

    @Test
    @DisplayName("A licence the member holds says what it opens")
    void accessComesFromTheLicence() throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO %s.user_license (account_id, license_id) VALUES (%d, %d)"
                    .formatted(schemaName, accountLinks.accountIdForDiscord(MEMBER), licenseId));
            statement.execute("INSERT INTO %s.license_access (license_id, release_type) VALUES (%d, 'STABLE')"
                    .formatted(schemaName, licenseId));
        }

        assertEquals(List.of(ReleaseType.STABLE), productRepository.accessByHolder(productId, MEMBER));
        assertEquals(List.of(licenseId), productRepository.licenseIdsFor(GUILD, productId, MEMBER));
    }

    @Test
    @DisplayName("A Discord role says what it opens, for that product and no other")
    void accessComesFromRoles() throws SQLException {
        int other;
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO %s.role_access (role_id, product_id, release_type) VALUES (%d, %d, 'DEV')"
                    .formatted(schemaName, 42L, productId));
            try (var rows = statement.executeQuery(
                    "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, 'Other', 6) RETURNING id"
                            .formatted(schemaName, GUILD))) {
                rows.next();
                other = rows.getInt(1);
            }
        }

        assertEquals(List.of(ReleaseType.DEV), productRepository.accessByRoles(productId, List.of(42L)));
        assertTrue(
                productRepository.accessByRoles(other, List.of(42L)).isEmpty(),
                "the role opens the product it was granted on, not every product");
        assertTrue(productRepository.accessByRoles(productId, List.of(99L)).isEmpty());
    }

    @Test
    @DisplayName("A column is written, and only for the product asked for")
    void setWritesOneProduct() throws SQLException {
        assertTrue(productRepository.set(productId, "name", c -> c.bind("Renamed")));

        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT name FROM %s.product WHERE id = %d".formatted(schemaName, productId))) {
            rows.next();
            assertEquals("Renamed", rows.getString(1));
        }
    }

    @Test
    @DisplayName("A product is deleted only by the guild that holds it")
    void deleteIsScopedToTheGuild() {
        assertFalse(productRepository.delete(productId, GUILD + 1), "another guild deletes nothing");
        assertTrue(productRepository.delete(productId, GUILD));
        assertFalse(productRepository.delete(productId, GUILD), "and it is gone");
    }
}
