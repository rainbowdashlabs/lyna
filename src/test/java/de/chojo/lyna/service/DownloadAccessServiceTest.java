/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.download.service.DownloadAccessService;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What an account may download on the web: what its licences carry, and what its stored roles open.
 */
class DownloadAccessServiceTest extends RepositoryTestBase {
    private static final long GUILD = 4701L;
    private static final long ROLE = 8101L;

    private DownloadAccessService access;
    private int licensed;
    private int roleOnly;
    private int unrelated;
    private int account;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "account_role",
                "role_access",
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "product",
                "account_identity",
                "account");
        access = new DownloadAccessService(accountLicenses, accountRoles, productRepository);
        account = accounts.insert(null).id();
        licensed = product("Licensed");
        roleOnly = product("Blood Night");
        unrelated = product("Unrelated");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            int license;
            try (var rows = statement.executeQuery("""
                    INSERT INTO %s.license (product_id, user_identifier, key)
                    VALUES (%d, 'buyer@example.invalid', 'ACCESS-KEY') RETURNING id
                    """.formatted(schemaName, licensed))) {
                rows.next();
                license = rows.getInt(1);
            }
            statement.execute("INSERT INTO %s.user_license (account_id, license_id) VALUES (%d, %d)"
                    .formatted(schemaName, account, license));
            statement.execute("INSERT INTO %s.license_access (license_id, release_type) VALUES (%d, 'STABLE')"
                    .formatted(schemaName, license));
            statement.execute("INSERT INTO %s.role_access (role_id, product_id, release_type) VALUES (%d, %d, 'STABLE')"
                    .formatted(schemaName, ROLE, roleOnly));
            statement.execute("INSERT INTO %s.role_access (role_id, product_id, release_type) VALUES (%d, %d, 'DEV')"
                    .formatted(schemaName, ROLE, licensed));
            statement.execute("INSERT INTO %s.role_access (role_id, product_id, release_type) VALUES (%d, %d, 'DEV')"
                    .formatted(schemaName, ROLE + 1, unrelated));
        }
    }

    private int product(String name) throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, '%s', 5) RETURNING id"
                                .formatted(schemaName, GUILD, name))) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private void wearing(Long... roleIds) {
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(roleIds));
    }

    @Test
    @DisplayName("A stored role opens its product, beside what the licences carry")
    void rolesAddToLicences() {
        wearing(ROLE);

        assertEquals(Set.of(licensed, roleOnly), access.entitledProductIds(account));
        assertEquals(Set.of("STABLE"), access.releaseTypes(account, roleOnly));
        assertEquals(Set.of("STABLE", "DEV"), access.releaseTypes(account, licensed));
        assertEquals(Set.of(), access.releaseTypes(account, unrelated), "a role opens only what it was granted on");
    }

    @Test
    @DisplayName("An account wearing no stored role has only its licences")
    void noRolesMeansLicencesOnly() {
        assertEquals(Set.of(licensed), access.entitledProductIds(account));
        assertEquals(Set.of("STABLE"), access.releaseTypes(account, licensed));
        assertEquals(Set.of(), access.releaseTypes(account, roleOnly));
    }
}
