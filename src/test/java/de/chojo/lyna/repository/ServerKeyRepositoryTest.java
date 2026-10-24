/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.updater.entity.HeldKey;
import de.chojo.lyna.feature.updater.entity.KeyKind;
import de.chojo.lyna.feature.updater.entity.ProductKeyType;
import de.chojo.lyna.feature.updater.entity.RaisedAllowance;
import de.chojo.lyna.feature.updater.entity.RetiredKey;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.entity.UpdateTokenRequest;
import de.chojo.lyna.feature.updater.repository.ProductKeyTypeRepository;
import de.chojo.lyna.feature.updater.repository.TrialHolderRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenQuotaRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server keys in the tables they share with upgrade tokens: their kind and key type, when they are
 * due for a successor and when they expire, the allowances and requests kept per key type, and the
 * trials that decide who may hold them.
 */
class ServerKeyRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 5101L;

    private final UpdateTokenRepository tokens = new UpdateTokenRepository();
    private final UpdateTokenQuotaRepository quota = new UpdateTokenQuotaRepository();
    private final ProductKeyTypeRepository keyTypes = new ProductKeyTypeRepository();
    private final TrialHolderRepository trials = new TrialHolderRepository();
    private int account;
    private int other;
    private int product;
    private int elsewhere;
    private ProductKeyType dashboard;
    private ProductKeyType proxy;
    private ProductKeyType foreign;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "update_token_request",
                "update_token_quota",
                "update_token_retired",
                "update_token",
                "product_key_type",
                "trial",
                "product",
                "account_identity",
                "account");
        account = accountService.register("dashboard@example.invalid", "hash").id();
        other = accounts.insert(null).id();
        product = product("Companies", GUILD);
        elsewhere = product("Elsewhere", GUILD + 1);
        dashboard = keyType(GUILD, product, "dashboard", "Dashboard key", 7, 30);
        proxy = keyType(GUILD, product, "proxy", "Proxy key", 7, 30);
        foreign = keyType(GUILD + 1, elsewhere, "dashboard", "Dashboard key", 7, 30);
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

    private ProductKeyType keyType(long guild, int productId, String slug, String name, int refresh, int expiry) {
        var draft = new ProductKeyType.Draft(slug, name, null, true, refresh, expiry, 10, 1);
        return keyTypes.create(guild, productId, draft, "lyg_" + slug + productId)
                .orElseThrow();
    }

    @Test
    @DisplayName("A server key carries its kind, its key type, when it was issued, and when it is due and expires")
    void createServerKey() {
        UpdateToken key = tokens.create(account, product, dashboard.id(), "lobby", "lyd_one");

        assertEquals(KeyKind.SERVER, key.kind());
        assertEquals(dashboard.id(), key.keyTypeId());
        assertNotNull(key.rotatedAt());
        assertNull(key.refreshedAt());
        assertEquals(Duration.ofDays(7), Duration.between(key.rotatedAt(), key.refreshAt()));
        assertEquals(Duration.ofDays(30), Duration.between(key.rotatedAt(), key.expiresAt()));
    }

    @Test
    @DisplayName("An upgrade token has no key type, and is neither due for a refresh nor expires")
    void upgradeTokenHasNoSchedule() {
        UpdateToken token = tokens.create(account, product, null, "lobby", "lyu_one");

        assertEquals(KeyKind.UPDATE, token.kind());
        assertNull(token.keyTypeId());
        assertNull(token.refreshAt());
        assertNull(token.expiresAt());
        assertNull(tokens.rotate("lyu_one", "lyu_two").orElseThrow().refreshedAt());
    }

    @Test
    @DisplayName("A server key without a key type, or an upgrade token with one, cannot be stored")
    void kindMatchesKeyType() {
        assertThrows(
                SQLException.class,
                () -> execute(("INSERT INTO %%s.update_token (account_id, product_id, kind, name, token)"
                                + " VALUES (%d, %d, 'SERVER', 'lobby', 'lyd_bare')")
                        .formatted(account, product)));
        assertThrows(
                SQLException.class,
                () -> execute(("INSERT INTO %%s.update_token (account_id, product_id, kind, key_type_id, name, token)"
                                + " VALUES (%d, %d, 'UPDATE', %d, 'lobby', 'lyu_typed')")
                        .formatted(account, product, dashboard.id())));
    }

    @Test
    @DisplayName("The schedule follows the key type's settings, also for keys issued before they changed")
    void scheduleFollowsKeyType() throws SQLException {
        UpdateToken key = tokens.create(account, product, dashboard.id(), "lobby", "lyd_one");
        execute("UPDATE %%s.update_token SET last_used_at = rotated_at + interval '1 day' WHERE id = %d"
                .formatted(key.id()));
        keyTypes.update(
                        GUILD,
                        dashboard.id(),
                        new ProductKeyType.Draft("dashboard", "Dashboard key", null, true, 2, 5, 10, 1))
                .orElseThrow();

        UpdateToken read = tokens.byId(key.id()).orElseThrow();

        assertEquals(Duration.ofDays(2), Duration.between(read.rotatedAt(), read.refreshAt()));
        assertEquals(Duration.ofDays(5), Duration.between(read.lastUsedAt(), read.expiresAt()));
        UpdateToken byProxy = tokens.create(account, product, proxy.id(), "lobby", "lyd_two");
        assertEquals(Duration.ofDays(7), Duration.between(byProxy.rotatedAt(), byProxy.refreshAt()));
    }

    @Test
    @DisplayName("Keys are counted per key type, and upgrade tokens on their own")
    void countPerKeyType() {
        tokens.create(account, product, dashboard.id(), "lobby", "lyd_one");
        tokens.create(account, product, dashboard.id(), "survival", "lyd_two");
        tokens.create(account, product, proxy.id(), "proxy", "lyd_three");
        tokens.create(account, product, null, "lobby", "lyu_one");

        assertEquals(2, tokens.count(account, product, dashboard.id()));
        assertEquals(1, tokens.count(account, product, proxy.id()));
        assertEquals(1, tokens.count(account, product, null));
        assertEquals(0, tokens.count(other, product, dashboard.id()));
        assertEquals(4, tokens.forAccount(account).size());
    }

    @Test
    @DisplayName("Rotating a server key retires the old one, reissues it and notes the refresh")
    void rotateServerKey() {
        UpdateToken key = tokens.create(account, product, dashboard.id(), "lobby", "lyd_one");

        UpdateToken rotated = tokens.rotate("lyd_one", "lyd_two").orElseThrow();

        assertEquals("lyd_two", rotated.token());
        assertEquals(dashboard.id(), rotated.keyTypeId());
        assertNotNull(rotated.refreshedAt());
        assertNotNull(rotated.lastUsedAt());
        assertFalse(rotated.rotatedAt().isBefore(key.rotatedAt()));
        RetiredKey retired = tokens.retired("lyd_one").orElseThrow();
        assertEquals(key.id(), retired.entryId());
        assertEquals(KeyKind.SERVER, retired.kind());
        assertNotNull(retired.retiredAt());
        assertTrue(tokens.retired("lyd_two").isEmpty(), "the current key is not retired");
        assertNull(tokens.byId(key.id()).orElseThrow().lastReuseAt(), "looking a key up is not reuse");
    }

    @Test
    @DisplayName("Touching notes a use, and a refresh only when the server asked for one")
    void touch() {
        UpdateToken key = tokens.create(account, product, dashboard.id(), "lobby", "lyd_one");

        UpdateToken verified = tokens.touch(key.id(), false).orElseThrow();
        assertNotNull(verified.lastUsedAt());
        assertNull(verified.refreshedAt());

        UpdateToken refreshed = tokens.touch(key.id(), true).orElseThrow();
        assertNotNull(refreshed.refreshedAt());
        assertEquals("lyd_one", refreshed.token());
        assertTrue(tokens.touch(key.id() + 1000, true).isEmpty());
    }

    @Test
    @DisplayName("Reissuing a server key starts its schedule again and keeps its key type")
    void reissue() throws SQLException {
        UpdateToken key = tokens.create(account, product, dashboard.id(), "lobby", "lyd_one");
        execute("UPDATE %%s.update_token SET rotated_at = rotated_at - interval '40 days' WHERE id = %d"
                .formatted(key.id()));

        UpdateToken reissued = tokens.reissue(key.id(), account, "lyd_fresh").orElseThrow();

        assertEquals(KeyKind.SERVER, reissued.kind());
        assertEquals(dashboard.id(), reissued.keyTypeId());
        assertTrue(reissued.rotatedAt().isAfter(key.rotatedAt().minus(Duration.ofDays(1))));
        assertTrue(tokens.retired("lyd_one").isPresent());
    }

    @Test
    @DisplayName("A guild sees the server keys held for its products with their key type, and revokes only those")
    void inGuild() {
        UpdateToken key = tokens.create(account, product, dashboard.id(), "lobby", "lyd_one");
        UpdateToken token = tokens.create(account, product, null, "lobby", "lyu_one");
        UpdateToken abroad = tokens.create(account, elsewhere, foreign.id(), "lobby", "lyd_two");

        List<HeldKey> held = tokens.inGuild(GUILD, KeyKind.SERVER);

        assertEquals(1, held.size());
        HeldKey first = held.getFirst();
        assertEquals(key.id(), first.id());
        assertEquals(account, first.accountId());
        assertNotNull(first.account());
        assertEquals("Companies", first.productName());
        assertEquals(KeyKind.SERVER, first.kind());
        assertEquals(dashboard.id(), first.keyTypeId());
        assertEquals("Dashboard key", first.keyTypeName());
        assertEquals("lobby", first.name());
        assertNotNull(first.expiresAt());
        assertNull(first.lastUsedAt());
        HeldKey upgrade = tokens.inGuild(GUILD, KeyKind.UPDATE).getFirst();
        assertNull(upgrade.keyTypeId());
        assertNull(upgrade.keyTypeName());
        assertNull(upgrade.expiresAt());

        assertFalse(tokens.deleteInGuild(GUILD, token.id(), KeyKind.SERVER), "not an upgrade token");
        assertFalse(tokens.deleteInGuild(GUILD, abroad.id(), KeyKind.SERVER), "not another guild's key");
        assertTrue(tokens.deleteInGuild(GUILD, key.id(), KeyKind.SERVER));
        assertTrue(tokens.byId(key.id()).isEmpty());
        assertTrue(tokens.byId(abroad.id()).isPresent());
    }

    @Test
    @DisplayName("Allowances are raised per key type, and only for a key type of the product")
    void quotaPerKeyType() {
        assertTrue(quota.setGranted(GUILD, account, product, dashboard.id(), 4));
        assertTrue(quota.setGranted(GUILD, account, product, proxy.id(), 1));
        assertTrue(quota.setGranted(GUILD, account, product, null, 2));
        assertFalse(quota.setGranted(GUILD, account, product, foreign.id(), 9), "another product's key type");
        tokens.create(account, product, dashboard.id(), "lobby", "lyd_one");

        assertEquals(4, quota.granted(account, product, dashboard.id()));
        assertEquals(1, quota.granted(account, product, proxy.id()));
        assertEquals(2, quota.granted(account, product, null));
        assertEquals(0, quota.granted(account, product, foreign.id()));

        List<RaisedAllowance> raised = quota.raisedInGuild(GUILD, KeyKind.SERVER);
        assertEquals(2, raised.size());
        RaisedAllowance first = raised.getFirst();
        assertEquals(KeyKind.SERVER, first.kind());
        assertEquals(dashboard.id(), first.keyTypeId());
        assertEquals("Dashboard key", first.keyTypeName());
        assertEquals(4, first.granted());
        assertEquals(1, first.used());
        assertEquals(0, raised.get(1).used());
        RaisedAllowance upgrade = quota.raisedInGuild(GUILD, KeyKind.UPDATE).getFirst();
        assertNull(upgrade.keyTypeId());
        assertEquals(0, upgrade.used());
    }

    @Test
    @DisplayName("One pending request per key type, and approving raises the key type asked for")
    void requestsPerKeyType() {
        UpdateTokenRequest forDashboard =
                quota.request(account, product, dashboard.id(), 3, "a network").orElseThrow();
        assertTrue(quota.request(account, product, proxy.id(), 1, "proxies").isPresent());
        assertTrue(quota.request(account, product, null, 1, "updaters").isPresent());
        assertTrue(quota.request(account, product, dashboard.id(), 1, "again").isEmpty());

        assertEquals(KeyKind.SERVER, forDashboard.kind());
        assertEquals(dashboard.id(), forDashboard.keyTypeId());
        assertEquals("Dashboard key", forDashboard.keyTypeName());
        assertEquals(2, quota.pendingInGuild(GUILD, KeyKind.SERVER).size());
        assertEquals(forDashboard, quota.pendingInGuild(GUILD, KeyKind.SERVER).getFirst());
        assertEquals(1, quota.pendingInGuild(GUILD, KeyKind.UPDATE).size());

        quota.decide(GUILD, forDashboard.id(), true, null);

        assertEquals(3, quota.granted(account, product, dashboard.id()));
        assertEquals(0, quota.granted(account, product, proxy.id()));
        assertEquals(0, quota.granted(account, product, null));
        assertTrue(quota.request(account, product, dashboard.id(), 1, "again").isPresent());
    }

    @Test
    @DisplayName("A trial counts when the account took it on the web or its Discord account took it on Discord")
    void trials() throws SQLException {
        assertFalse(trials.tookTrial(account, product));
        assertEquals(List.of(), trials.trialProductIds(account));

        execute("INSERT INTO %%s.trial (product_id, account_id) VALUES (%d, %d)".formatted(product, account));
        assertTrue(trials.tookTrial(account, product));
        assertFalse(trials.tookTrial(other, product));

        execute(("INSERT INTO %%s.account_identity (provider, external_id, account_id, verified_via)"
                        + " VALUES ('discord', '777', %d, 'oauth')")
                .formatted(other));
        execute("INSERT INTO %%s.trial (product_id, user_id) VALUES (%d, 777)".formatted(elsewhere));
        assertTrue(trials.tookTrial(other, elsewhere));
        assertEquals(List.of(elsewhere), trials.trialProductIds(other));
        assertEquals(List.of(product), trials.trialProductIds(account));
    }
}
