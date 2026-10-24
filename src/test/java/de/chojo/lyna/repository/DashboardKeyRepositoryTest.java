/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.updater.entity.DashboardSettings;
import de.chojo.lyna.feature.updater.entity.HeldKey;
import de.chojo.lyna.feature.updater.entity.KeyKind;
import de.chojo.lyna.feature.updater.entity.RetiredKey;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.repository.DashboardProductRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenQuotaRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dashboard keys in the tables they share with upgrade tokens: their kind, when they are due for a
 * successor and when they expire, the allowances and requests kept per kind, and the product settings
 * and trials that decide who may hold them.
 */
class DashboardKeyRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 5101L;

    private final UpdateTokenRepository tokens = new UpdateTokenRepository();
    private final UpdateTokenQuotaRepository quota = new UpdateTokenQuotaRepository();
    private final DashboardProductRepository dashboards = new DashboardProductRepository();
    private int account;
    private int other;
    private int product;
    private int elsewhere;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "update_token_request",
                "update_token_quota",
                "update_token_retired",
                "update_token",
                "trial",
                "product",
                "account_identity",
                "account");
        account = accountService.register("dashboard@example.invalid", "hash").id();
        other = accounts.insert(null).id();
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

    @Test
    @DisplayName("A dashboard key carries its kind, when it was issued, and when it is due and expires")
    void createDashboardKey() {
        UpdateToken key = tokens.create(KeyKind.DASHBOARD, account, product, "lobby", "lyd_one");

        assertEquals(KeyKind.DASHBOARD, key.kind());
        assertNotNull(key.rotatedAt());
        assertNull(key.refreshedAt());
        assertEquals(Duration.ofDays(7), Duration.between(key.rotatedAt(), key.refreshAt()));
        assertEquals(Duration.ofDays(30), Duration.between(key.rotatedAt(), key.expiresAt()));
    }

    @Test
    @DisplayName("An upgrade token is neither due for a refresh nor expires")
    void upgradeTokenHasNoSchedule() {
        UpdateToken token = tokens.create(KeyKind.UPDATE, account, product, "lobby", "lyu_one");

        assertEquals(KeyKind.UPDATE, token.kind());
        assertNull(token.refreshAt());
        assertNull(token.expiresAt());
        assertNull(tokens.rotate("lyu_one", "lyu_two").orElseThrow().refreshedAt());
    }

    @Test
    @DisplayName("The schedule follows the product's settings, and expiry counts from the last use")
    void scheduleFollowsSettings() throws SQLException {
        assertTrue(dashboards.set(GUILD, product, new DashboardSettings(true, 2, 5, 3)));
        UpdateToken key = tokens.create(KeyKind.DASHBOARD, account, product, "lobby", "lyd_one");
        execute("UPDATE %%s.update_token SET last_used_at = rotated_at + interval '1 day' WHERE id = %d"
                .formatted(key.id()));

        UpdateToken read = tokens.byId(key.id()).orElseThrow();

        assertEquals(Duration.ofDays(2), Duration.between(read.rotatedAt(), read.refreshAt()));
        assertEquals(Duration.ofDays(5), Duration.between(read.lastUsedAt(), read.expiresAt()));
    }

    @Test
    @DisplayName("Keys are counted per kind")
    void countPerKind() {
        tokens.create(KeyKind.DASHBOARD, account, product, "lobby", "lyd_one");
        tokens.create(KeyKind.DASHBOARD, account, product, "survival", "lyd_two");
        tokens.create(KeyKind.UPDATE, account, product, "lobby", "lyu_one");

        assertEquals(2, tokens.count(account, product, KeyKind.DASHBOARD));
        assertEquals(1, tokens.count(account, product, KeyKind.UPDATE));
        assertEquals(3, tokens.forAccount(account).size());
    }

    @Test
    @DisplayName("Rotating a dashboard key retires the old one, reissues it and notes the refresh")
    void rotateDashboardKey() {
        UpdateToken key = tokens.create(KeyKind.DASHBOARD, account, product, "lobby", "lyd_one");

        UpdateToken rotated = tokens.rotate("lyd_one", "lyd_two").orElseThrow();

        assertEquals("lyd_two", rotated.token());
        assertNotNull(rotated.refreshedAt());
        assertNotNull(rotated.lastUsedAt());
        assertFalse(rotated.rotatedAt().isBefore(key.rotatedAt()));
        RetiredKey retired = tokens.retired("lyd_one").orElseThrow();
        assertEquals(key.id(), retired.entryId());
        assertEquals(KeyKind.DASHBOARD, retired.kind());
        assertNotNull(retired.retiredAt());
        assertTrue(tokens.retired("lyd_two").isEmpty(), "the current key is not retired");
        assertNull(tokens.byId(key.id()).orElseThrow().lastReuseAt(), "looking a key up is not reuse");
    }

    @Test
    @DisplayName("Touching notes a use, and a refresh only when the server asked for one")
    void touch() {
        UpdateToken key = tokens.create(KeyKind.DASHBOARD, account, product, "lobby", "lyd_one");

        UpdateToken verified = tokens.touch(key.id(), false).orElseThrow();
        assertNotNull(verified.lastUsedAt());
        assertNull(verified.refreshedAt());

        UpdateToken refreshed = tokens.touch(key.id(), true).orElseThrow();
        assertNotNull(refreshed.refreshedAt());
        assertEquals("lyd_one", refreshed.token());
        assertTrue(tokens.touch(key.id() + 1000, true).isEmpty());
    }

    @Test
    @DisplayName("Reissuing a dashboard key starts its schedule again")
    void reissue() throws SQLException {
        UpdateToken key = tokens.create(KeyKind.DASHBOARD, account, product, "lobby", "lyd_one");
        execute("UPDATE %%s.update_token SET rotated_at = rotated_at - interval '40 days' WHERE id = %d"
                .formatted(key.id()));

        UpdateToken reissued = tokens.reissue(key.id(), account, "lyd_fresh").orElseThrow();

        assertEquals(KeyKind.DASHBOARD, reissued.kind());
        assertTrue(reissued.rotatedAt().isAfter(key.rotatedAt().minus(Duration.ofDays(1))));
        assertTrue(tokens.retired("lyd_one").isPresent());
    }

    @Test
    @DisplayName("A guild sees the keys of one kind held for its products, and revokes only those")
    void inGuild() {
        UpdateToken key = tokens.create(KeyKind.DASHBOARD, account, product, "lobby", "lyd_one");
        UpdateToken token = tokens.create(KeyKind.UPDATE, account, product, "lobby", "lyu_one");
        UpdateToken foreign = tokens.create(KeyKind.DASHBOARD, account, elsewhere, "lobby", "lyd_two");

        List<HeldKey> held = tokens.inGuild(GUILD, KeyKind.DASHBOARD);

        assertEquals(1, held.size());
        HeldKey first = held.getFirst();
        assertEquals(key.id(), first.id());
        assertEquals(account, first.accountId());
        assertNotNull(first.account());
        assertEquals("Companies", first.productName());
        assertEquals(KeyKind.DASHBOARD, first.kind());
        assertEquals("lobby", first.name());
        assertNotNull(first.expiresAt());
        assertNull(first.lastUsedAt());
        assertEquals(1, tokens.inGuild(GUILD, KeyKind.UPDATE).size());

        assertFalse(tokens.deleteInGuild(GUILD, token.id(), KeyKind.DASHBOARD), "not an upgrade token");
        assertFalse(tokens.deleteInGuild(GUILD, foreign.id(), KeyKind.DASHBOARD), "not another guild's key");
        assertTrue(tokens.deleteInGuild(GUILD, key.id(), KeyKind.DASHBOARD));
        assertTrue(tokens.byId(key.id()).isEmpty());
        assertTrue(tokens.byId(foreign.id()).isPresent());
    }

    @Test
    @DisplayName("Allowances are raised per kind")
    void quotaPerKind() {
        assertTrue(quota.setGranted(GUILD, account, product, KeyKind.DASHBOARD, 4));
        assertTrue(quota.setGranted(GUILD, account, product, KeyKind.UPDATE, 2));
        tokens.create(KeyKind.DASHBOARD, account, product, "lobby", "lyd_one");

        assertEquals(4, quota.granted(account, product, KeyKind.DASHBOARD));
        assertEquals(2, quota.granted(account, product, KeyKind.UPDATE));
        var raised = quota.raisedInGuild(GUILD, KeyKind.DASHBOARD);
        assertEquals(1, raised.size());
        assertEquals(KeyKind.DASHBOARD, raised.getFirst().kind());
        assertEquals(4, raised.getFirst().granted());
        assertEquals(1, raised.getFirst().used());
        assertEquals(0, quota.raisedInGuild(GUILD, KeyKind.UPDATE).getFirst().used());
    }

    @Test
    @DisplayName("One pending request per kind, and approving raises the kind asked for")
    void requestsPerKind() {
        var dashboard = quota.request(account, product, KeyKind.DASHBOARD, 3, "a network")
                .orElseThrow();
        assertTrue(
                quota.request(account, product, KeyKind.UPDATE, 1, "updaters").isPresent());
        assertTrue(
                quota.request(account, product, KeyKind.DASHBOARD, 1, "again").isEmpty());

        assertEquals(KeyKind.DASHBOARD, dashboard.kind());
        assertEquals(List.of(dashboard), quota.pendingInGuild(GUILD, KeyKind.DASHBOARD));
        assertEquals(1, quota.pendingInGuild(GUILD, KeyKind.UPDATE).size());

        quota.decide(GUILD, dashboard.id(), true, null);

        assertEquals(3, quota.granted(account, product, KeyKind.DASHBOARD));
        assertEquals(0, quota.granted(account, product, KeyKind.UPDATE));
    }

    @Test
    @DisplayName("A product starts with dashboard keys off and the default schedule, and only its guild changes it")
    void settings() {
        assertEquals(Optional.of(DashboardSettings.DEFAULT), dashboards.settings(product));
        assertTrue(dashboards.settings(-1).isEmpty());

        DashboardSettings changed = new DashboardSettings(true, 3, 14, 5);
        assertFalse(dashboards.set(GUILD + 1, product, changed));
        assertTrue(dashboards.set(GUILD, product, changed));

        assertEquals(Optional.of(changed), dashboards.settings(product));
    }

    @Test
    @DisplayName("A trial counts when the account took it on the web or its Discord account took it on Discord")
    void trials() throws SQLException {
        assertFalse(dashboards.tookTrial(account, product));
        assertEquals(List.of(), dashboards.trialProductIds(account));

        execute("INSERT INTO %%s.trial (product_id, account_id) VALUES (%d, %d)".formatted(product, account));
        assertTrue(dashboards.tookTrial(account, product));
        assertFalse(dashboards.tookTrial(other, product));

        execute(("INSERT INTO %%s.account_identity (provider, external_id, account_id, verified_via)"
                        + " VALUES ('discord', '777', %d, 'oauth')")
                .formatted(other));
        execute("INSERT INTO %%s.trial (product_id, user_id) VALUES (%d, 777)".formatted(elsewhere));
        assertTrue(dashboards.tookTrial(other, elsewhere));
        assertEquals(List.of(elsewhere), dashboards.trialProductIds(other));
        assertEquals(List.of(product), dashboards.trialProductIds(account));
    }
}
