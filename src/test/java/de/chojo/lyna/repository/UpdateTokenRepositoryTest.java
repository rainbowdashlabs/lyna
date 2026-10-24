/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.updater.entity.KeyKind;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.entity.UpdateTokenRequest;
import de.chojo.lyna.feature.updater.repository.UpdateTokenQuotaRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateTokenRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 4801L;

    private final UpdateTokenRepository tokens = new UpdateTokenRepository();
    private final UpdateTokenQuotaRepository quota = new UpdateTokenQuotaRepository();
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
                "product",
                "account_identity",
                "account");
        account = accountService.register("updater@example.invalid", "hash").id();
        other = accounts.insert(null).id();
        product = product("Chatty", GUILD);
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
    @DisplayName("A created entry is found by its id, its token and its account")
    void createAndFind() {
        UpdateToken created = tokens.create(account, product, null, "lobby", "lyu_one");

        assertEquals("lobby", created.name());
        assertEquals("Chatty", created.productName());
        assertEquals("lyu_one", created.token());
        assertNotNull(created.createdAt());
        assertNull(created.lastUsedAt());
        assertNull(created.lastReuseAt());
        assertEquals(created, tokens.byToken("lyu_one").orElseThrow());
        assertEquals(List.of(created), tokens.forAccount(account));
        assertEquals(1, tokens.count(account, product, null));
        assertEquals(0, tokens.count(account, elsewhere, null));
        assertEquals(0, tokens.count(other, product, null));
    }

    @Test
    @DisplayName("A trade replaces the token, retires the old one and notes when it happened")
    void rotate() {
        tokens.create(account, product, null, "lobby", "lyu_one");

        UpdateToken rotated = tokens.rotate("lyu_one", "lyu_two").orElseThrow();

        assertEquals("lyu_two", rotated.token());
        assertNotNull(rotated.lastUsedAt());
        assertTrue(tokens.byToken("lyu_one").isEmpty());
        assertTrue(tokens.rotate("lyu_one", "lyu_three").isEmpty(), "a retired token trades nothing");
    }

    @Test
    @DisplayName("Of two trades of one token at the same moment, exactly one succeeds")
    void concurrentRotation() throws Exception {
        tokens.create(account, product, null, "lobby", "lyu_race");

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Optional<UpdateToken>> first = () -> tokens.rotate("lyu_race", "lyu_a");
            Callable<Optional<UpdateToken>> second = () -> tokens.rotate("lyu_race", "lyu_b");
            var a = CompletableFuture.supplyAsync(() -> call(first), executor);
            var b = CompletableFuture.supplyAsync(() -> call(second), executor);
            long won = List.of(a.get(), b.get()).stream()
                    .filter(Optional::isPresent)
                    .count();
            assertEquals(1, won);
        }
    }

    private static Optional<UpdateToken> call(Callable<Optional<UpdateToken>> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("A retired token presented again is recorded on its entry; an unknown one finds nothing")
    void markReused() {
        UpdateToken created = tokens.create(account, product, null, "lobby", "lyu_one");
        tokens.rotate("lyu_one", "lyu_two");

        UpdateToken owner = tokens.markReused("lyu_one").orElseThrow();

        assertEquals(created.id(), owner.id());
        assertNotNull(owner.lastReuseAt());
        assertTrue(tokens.markReused("lyu_never").isEmpty());
    }

    @Test
    @DisplayName("Reissuing gives a new token, and the old one is recognised as reused afterwards")
    void reissue() {
        UpdateToken created = tokens.create(account, product, null, "lobby", "lyu_one");

        UpdateToken reissued =
                tokens.reissue(created.id(), account, "lyu_fresh").orElseThrow();

        assertEquals("lyu_fresh", reissued.token());
        assertEquals("lobby", reissued.name());
        assertTrue(tokens.byToken("lyu_one").isEmpty());
        assertTrue(tokens.markReused("lyu_one").isPresent());
        assertTrue(tokens.reissue(created.id(), other, "lyu_stolen").isEmpty(), "only the owner reissues");
    }

    @Test
    @DisplayName("Renaming and revoking reach only the account's own entries")
    void renameAndDelete() {
        UpdateToken created = tokens.create(account, product, null, "lobby", "lyu_one");

        assertFalse(tokens.rename(created.id(), other, "theirs"));
        assertTrue(tokens.rename(created.id(), account, "survival"));
        assertEquals("survival", tokens.byId(created.id()).orElseThrow().name());

        assertFalse(tokens.delete(created.id(), other));
        assertTrue(tokens.delete(created.id(), account));
        assertTrue(tokens.byId(created.id()).isEmpty());
    }

    @Test
    @DisplayName("An allowance starts at nothing granted, is set per guild, and can be lowered again")
    void granted() {
        assertEquals(0, quota.granted(account, product, null));
        assertEquals(List.of(), quota.raisedInGuild(GUILD, KeyKind.UPDATE));

        assertTrue(quota.setGranted(GUILD, account, product, null, 5));
        tokens.create(account, product, null, "lobby", "lyu_one");
        assertEquals(5, quota.granted(account, product, null));
        assertEquals(1, quota.raisedInGuild(GUILD, KeyKind.UPDATE).size());
        var raised = quota.raisedInGuild(GUILD, KeyKind.UPDATE).getFirst();
        assertEquals(5, raised.granted());
        assertEquals(1, raised.used());
        assertEquals("Chatty", raised.productName());
        assertEquals(account, raised.accountId());
        assertEquals(product, raised.productId());
        assertNotNull(raised.account());

        assertFalse(quota.setGranted(GUILD + 5, account, product, null, 9), "another guild's admin cannot");
        assertEquals(5, quota.granted(account, product, null));

        quota.setGranted(GUILD, account, product, null, -3);
        assertEquals(0, quota.granted(account, product, null), "an allowance never goes below the default");
        assertEquals(List.of(), quota.raisedInGuild(GUILD, KeyKind.UPDATE));
    }

    @Test
    @DisplayName("One pending request per product; approving adds what it asked for")
    void requestAndApprove() {
        UpdateTokenRequest request =
                quota.request(account, product, null, 5, "a network").orElseThrow();

        assertEquals(UpdateTokenRequest.Status.PENDING, request.status());
        assertEquals("Chatty", request.productName());
        assertNotNull(request.account());
        assertTrue(quota.request(account, product, null, 3, "again").isEmpty(), "one request waits at a time");
        assertEquals(List.of(request), quota.pendingInGuild(GUILD, KeyKind.UPDATE));
        assertEquals(List.of(), quota.pendingInGuild(GUILD + 1, KeyKind.UPDATE));

        UpdateTokenRequest approved =
                quota.decide(GUILD, request.id(), true, 77L).orElseThrow();

        assertEquals(UpdateTokenRequest.Status.APPROVED, approved.status());
        assertNotNull(approved.decidedAt());
        assertEquals(5, quota.granted(account, product, null));
        assertTrue(quota.decide(GUILD, request.id(), true, 77L).isEmpty(), "a settled request stays settled");
        assertEquals(5, quota.granted(account, product, null));

        UpdateTokenRequest second =
                quota.request(account, product, null, 2, "more").orElseThrow();
        quota.decide(GUILD, second.id(), true, null);
        assertEquals(7, quota.granted(account, product, null), "a second approval adds to the first");
    }

    @Test
    @DisplayName("Declining grants nothing, and another guild cannot settle the request")
    void decline() {
        UpdateTokenRequest request =
                quota.request(account, product, null, 5, "a network").orElseThrow();

        assertTrue(quota.decide(GUILD + 1, request.id(), true, 1L).isEmpty());
        UpdateTokenRequest declined =
                quota.decide(GUILD, request.id(), false, 1L).orElseThrow();

        assertEquals(UpdateTokenRequest.Status.DECLINED, declined.status());
        assertEquals(0, quota.granted(account, product, null));
        assertEquals(List.of(declined), quota.requestsOf(account));
        assertTrue(quota.request(account, product, null, 1, "please").isPresent(), "a settled request makes room");
    }
}
