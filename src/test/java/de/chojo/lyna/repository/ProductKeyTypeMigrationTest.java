/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.sadu.core.updater.SqlVersion;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The migration that turned the one global dashboard key into key types of each product: a database
 * holding dashboard keys, allowances and requests before it keeps every one of them afterwards, under
 * a "dashboard" key type carrying the product's former settings.
 *
 * <p>The data is written by a hook the updater runs right before the migration, on a schema of this
 * class's own, so it is written against the tables exactly as they were.
 */
class ProductKeyTypeMigrationTest extends RepositoryTestBase {
    private static final String SCHEMA = "lyna_key_type_migration";
    private static final SqlVersion KEY_TYPES = new SqlVersion(1, 24);

    private static HikariDataSource migrated;

    @BeforeAll
    static void migrate() throws Exception {
        migrated = DataSourceCreator.create(PostgreSql.get())
                .configure(config -> config.host(PG.getHost())
                        .port(PG.getFirstMappedPort())
                        .user(PG.getUsername())
                        .password(PG.getPassword())
                        .database(PG.getDatabaseName()))
                .create()
                .withMaximumPoolSize(2)
                .forSchema(SCHEMA)
                .build();
        SqlUpdater.builder(migrated, PostgreSql.get())
                .setReplacements(new QueryReplacement("lyna", SCHEMA))
                .setVersionTable(SCHEMA + ".lyna_version")
                .setSchemas(SCHEMA)
                .preUpdateHook(KEY_TYPES, ProductKeyTypeMigrationTest::seedBefore)
                .execute();
    }

    @AfterAll
    static void close() {
        migrated.close();
    }

    /**
     * Writes, against the tables as they were before key types, a product offering dashboard keys with
     * settings of its own, one that offered none but still has keys, and one that never had any.
     */
    private static void seedBefore(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(("""
                    INSERT INTO %1$s.account (id, username) VALUES (1, 'buyer');
                    INSERT INTO %1$s.product (id, guild_id, name, role, dashboard_keys, dashboard_refresh_days,
                                              dashboard_expiry_days, dashboard_allowance)
                    VALUES (1, 1, 'Companies', 5, TRUE, 3, 14, 4),
                           (2, 1, 'Retired', 5, FALSE, 7, 30, 10),
                           (3, 1, 'Plain', 5, FALSE, 7, 30, 10);
                    INSERT INTO %1$s.update_token (id, account_id, product_id, kind, name, token, rotated_at)
                    VALUES (1, 1, 1, 'DASHBOARD', 'lobby', 'lyd_lobby', now()),
                           (2, 1, 1, 'UPDATE', 'updater', 'lyu_updater', now()),
                           (3, 1, 2, 'DASHBOARD', 'old', 'lyd_old', now()),
                           (4, 1, 3, 'UPDATE', 'plain', 'lyu_plain', now());
                    INSERT INTO %1$s.update_token_retired (token, update_token_id) VALUES ('lyd_before', 1);
                    INSERT INTO %1$s.update_token_quota (account_id, product_id, kind, granted)
                    VALUES (1, 1, 'DASHBOARD', 2), (1, 1, 'UPDATE', 5);
                    INSERT INTO %1$s.update_token_request (account_id, product_id, kind, amount, reason)
                    VALUES (1, 1, 'DASHBOARD', 3, 'a network'), (1, 1, 'UPDATE', 1, 'updaters');
                    """).formatted(SCHEMA));
        }
    }

    private static List<List<String>> rows(String sql) throws SQLException {
        try (var connection = migrated.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql.formatted(SCHEMA))) {
            List<List<String>> result = new ArrayList<>();
            int columns = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columns; i++) row.add(rows.getString(i));
                result.add(row);
            }
            return result;
        }
    }

    @Test
    @DisplayName("A product that had dashboard keys gets a dashboard key type carrying its former settings")
    void keyTypesFromSettings() throws SQLException {
        assertEquals(
                List.of(
                        List.of(
                                "1",
                                "dashboard",
                                "Dashboard key",
                                "/companyadmin dashboard key {key}",
                                "t",
                                "3",
                                "14",
                                "4",
                                "1"),
                        List.of(
                                "2",
                                "dashboard",
                                "Dashboard key",
                                "/companyadmin dashboard key {key}",
                                "f",
                                "7",
                                "30",
                                "10",
                                "1")),
                rows("""
                        SELECT product_id, slug, name, command, enabled, refresh_days, expiry_days, allowance,
                               trial_allowance
                        FROM %s.product_key_type ORDER BY product_id
                        """));
    }

    @Test
    @DisplayName("Every key type gets a gateway secret of its own")
    void secrets() throws SQLException {
        List<List<String>> secrets = rows("SELECT gateway_secret FROM %s.product_key_type ORDER BY product_id");

        assertEquals(2, secrets.size());
        for (List<String> secret : secrets) {
            assertTrue(secret.getFirst().matches("^lyg_[0-9a-f]{64}$"), secret.getFirst());
        }
        assertNotEquals(secrets.get(0), secrets.get(1));
    }

    @Test
    @DisplayName(
            "Dashboard keys become server keys of their product's dashboard type; upgrade tokens stay as they were")
    void keysKept() throws SQLException {
        assertEquals(
                List.of(
                        List.of("1", "SERVER", "1", "lyd_lobby"),
                        List.of("2", "UPDATE", "-", "lyu_updater"),
                        List.of("3", "SERVER", "2", "lyd_old"),
                        List.of("4", "UPDATE", "-", "lyu_plain")),
                rows("""
                        SELECT t.id, t.kind, coalesce(k.product_id::TEXT, '-'), t.token
                        FROM %1$s.update_token t
                        LEFT JOIN %1$s.product_key_type k ON k.id = t.key_type_id
                        ORDER BY t.id
                        """));
        assertEquals(List.of(List.of("1")), rows("SELECT update_token_id FROM %s.update_token_retired"));
    }

    @Test
    @DisplayName("Allowances and requests for dashboard keys move to the dashboard type, and upgrade ones stay")
    void quotaAndRequestsKept() throws SQLException {
        assertEquals(List.of(List.of("SERVER", "dashboard", "2"), List.of("UPDATE", "-", "5")), rows("""
                        SELECT q.kind, coalesce(k.slug, '-'), q.granted
                        FROM %s.update_token_quota q
                        LEFT JOIN %<s.product_key_type k ON k.id = q.key_type_id
                        ORDER BY q.kind
                        """));
        assertEquals(List.of(List.of("SERVER", "dashboard", "3"), List.of("UPDATE", "-", "1")), rows("""
                        SELECT r.kind, coalesce(k.slug, '-'), r.amount
                        FROM %s.update_token_request r
                        LEFT JOIN %<s.product_key_type k ON k.id = r.key_type_id
                        ORDER BY r.kind
                        """));
    }

    @Test
    @DisplayName("Nothing called a dashboard is left behind")
    void nothingLeft() throws SQLException {
        assertEquals(List.of(), rows("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = '%s' AND table_name = 'product' AND column_name LIKE 'dashboard%%'
                        """));
        for (String table : List.of("update_token", "update_token_quota", "update_token_request")) {
            assertFalse(
                    rows("SELECT 1 FROM %s." + table + " WHERE kind = 'DASHBOARD'")
                            .iterator()
                            .hasNext(),
                    table);
        }
    }
}
