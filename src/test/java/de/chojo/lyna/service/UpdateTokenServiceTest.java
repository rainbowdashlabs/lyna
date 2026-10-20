/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.download.service.DownloadAccessService;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.entity.UpdateTokenRequest;
import de.chojo.lyna.feature.updater.repository.UpdateTokenQuotaRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenRepository;
import de.chojo.lyna.feature.updater.service.UpdateTokenService;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Refusal;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Upgrade tokens: who may hold them, how many, and what trading one in does.
 */
class UpdateTokenServiceTest extends RepositoryTestBase {
    private static final long GUILD = 4901L;
    private static final long ROLE = 9101L;

    private UpdateTokenService service;
    private int account;
    private int premium;
    private int free;
    private int unrelated;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "update_token_request",
                "update_token_quota",
                "update_token_retired",
                "update_token",
                "account_role",
                "role_access",
                "product",
                "account_identity",
                "account");
        service = service(Clock.systemUTC());
        account = accountService.register("trader@example.invalid", "hash").id();
        premium = product("Premium", false);
        free = product("Free", true);
        unrelated = product("Unrelated", false);
        execute("INSERT INTO %%s.role_access (role_id, product_id, release_type) VALUES (%d, %d, 'STABLE')"
                .formatted(ROLE, premium));
        accountRoles.replaceDiscordRoles(account, GUILD, List.of(ROLE));
    }

    private UpdateTokenService service(Clock clock) {
        return new UpdateTokenService(
                new UpdateTokenRepository(),
                new UpdateTokenQuotaRepository(),
                new DownloadAccessService(accountLicenses, accountRoles, productRepository),
                kioskProducts,
                clock);
    }

    private int product(String name, boolean isFree) throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "INSERT INTO %s.product (guild_id, name, role, free) VALUES (%d, '%s', 5, %b) RETURNING id"
                                .formatted(schemaName, GUILD, name, isFree))) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private UpdateToken created(String name) {
        return service.create(account, premium, name).value();
    }

    @Test
    @DisplayName("A token is made for a premium product the account may download, under a trimmed name")
    void create() {
        var outcome = service.create(account, premium, "  lobby  ");

        assertTrue(outcome.ok());
        assertEquals("lobby", outcome.value().name());
        assertTrue(outcome.value().token().startsWith("lyu_"));
        assertEquals(List.of(outcome.value()), service.tokens(account));
    }

    @Test
    @DisplayName("No token for a free product, one the account may not download, or without a name")
    void createRefused() {
        assertEquals(Refusal.FREE, service.create(account, free, "lobby").refusal());
        assertEquals(
                Refusal.NO_ACCESS, service.create(account, unrelated, "lobby").refusal());
        assertEquals(Refusal.INVALID_NAME, service.create(account, premium, " ").refusal());
        assertEquals(
                Refusal.INVALID_NAME, service.create(account, premium, null).refusal());
        assertEquals(
                Refusal.INVALID_NAME,
                service.create(account, premium, "x".repeat(UpdateTokenService.MAX_NAME_LENGTH + 1))
                        .refusal());
    }

    @Test
    @DisplayName("Ten tokens per product, and an approved request makes room for more")
    void allowance() {
        for (int i = 0; i < UpdateTokenService.DEFAULT_ALLOWANCE; i++) created("server " + i);

        assertEquals(
                Refusal.LIMIT_REACHED,
                service.create(account, premium, "one more").refusal());
        assertEquals(List.of(new UpdateTokenService.Allowance(premium, 10, 10)), service.allowances(account));

        UpdateTokenRequest request =
                service.requestMore(account, premium, 2, " a network ").value();
        assertEquals("a network", request.reason());
        assertEquals(List.of(request), service.requests(account));
        assertEquals(List.of(request), service.pending(GUILD));
        assertEquals(
                Refusal.PENDING,
                service.requestMore(account, premium, 1, "again").refusal());

        assertTrue(service.decide(GUILD, request.id(), true, 5L).ok());

        assertEquals(12, service.allowed(account, premium));
        assertEquals(2, service.raised(GUILD).getFirst().granted());
        assertTrue(service.create(account, premium, "one more").ok());
        assertEquals(
                Refusal.NOT_FOUND, service.decide(GUILD, request.id(), true, 5L).refusal());
    }

    @Test
    @DisplayName("A request has to ask for something, say why, and be for a product the account may have")
    void requestRefused() {
        assertEquals(
                Refusal.INVALID_REQUEST,
                service.requestMore(account, premium, 0, "why").refusal());
        assertEquals(
                Refusal.INVALID_REQUEST,
                service.requestMore(account, premium, UpdateTokenService.MAX_REQUEST + 1, "why")
                        .refusal());
        assertEquals(
                Refusal.INVALID_REQUEST,
                service.requestMore(account, premium, 1, " ").refusal());
        assertEquals(
                Refusal.INVALID_REQUEST,
                service.requestMore(account, premium, 1, null).refusal());
        assertEquals(
                Refusal.INVALID_REQUEST,
                service.requestMore(account, premium, 1, "x".repeat(UpdateTokenService.MAX_REASON_LENGTH + 1))
                        .refusal());
        assertEquals(Refusal.FREE, service.requestMore(account, free, 1, "why").refusal());
        assertEquals(
                Refusal.NO_ACCESS,
                service.requestMore(account, unrelated, 1, "why").refusal());
    }

    @Test
    @DisplayName("A trade hands out a new token; the old one is refused and recorded as reused")
    void trade() {
        UpdateToken token = created("lobby");

        assertTrue(service.resolve(token.token()).ok());
        UpdateToken traded = service.trade(token.token()).value();

        assertNotEquals(token.token(), traded.token());
        assertNotNull(traded.lastUsedAt());
        assertEquals(Refusal.REUSED, service.trade(token.token()).refusal());
        assertEquals(Refusal.REUSED, service.resolve(token.token()).refusal());
        assertNotNull(service.tokens(account).getFirst().lastReuseAt());
    }

    @Test
    @DisplayName("A token nobody issued is unknown")
    void unknown() {
        assertEquals(Refusal.UNKNOWN, service.resolve("lyu_nobody").refusal());
        assertEquals(Refusal.UNKNOWN, service.resolve(" ").refusal());
        assertEquals(Refusal.UNKNOWN, service.resolve(null).refusal());
        assertEquals(Refusal.UNKNOWN, service.trade("lyu_nobody").refusal());
    }

    @Test
    @DisplayName("A token traded moments ago has to wait before the next trade")
    void tooSoon() {
        UpdateToken traded = service.trade(created("lobby").token()).value();

        assertEquals(Refusal.TOO_SOON, service.resolve(traded.token()).refusal());

        Clock later = Clock.fixed(
                Instant.now().plus(UpdateTokenService.MIN_TRADE_INTERVAL).plusSeconds(1), ZoneOffset.UTC);
        assertTrue(service(later).resolve(traded.token()).ok());
    }

    @Test
    @DisplayName("Losing access refuses the token without ending it, and access coming back revives it")
    void accessLost() {
        UpdateToken token = created("lobby");
        accountRoles.replaceDiscordRoles(account, GUILD, List.of());

        assertEquals(Refusal.NO_ACCESS, service.resolve(token.token()).refusal());
        assertEquals(List.of(token), service.tokens(account));

        accountRoles.replaceDiscordRoles(account, GUILD, List.of(ROLE));
        assertTrue(service.resolve(token.token()).ok());
    }

    @Test
    @DisplayName("Renaming, reissuing and revoking an entry")
    void manage() {
        UpdateToken token = created("lobby");

        assertEquals(
                "survival",
                service.rename(account, token.id(), "survival").value().name());
        assertEquals(
                Refusal.INVALID_NAME, service.rename(account, token.id(), "").refusal());
        assertEquals(
                Refusal.NOT_FOUND,
                service.rename(account, token.id() + 1000, "x").refusal());

        UpdateToken reissued = service.reissue(account, token.id()).value();
        assertNotEquals(token.token(), reissued.token());
        assertEquals(Refusal.REUSED, service.resolve(token.token()).refusal());
        assertEquals(
                Refusal.NOT_FOUND, service.reissue(account, token.id() + 1000).refusal());

        assertTrue(service.revoke(account, token.id()));
        assertFalse(service.revoke(account, token.id()));
        assertEquals(Refusal.UNKNOWN, service.resolve(reissued.token()).refusal());
    }

    @Test
    @DisplayName("Lowering an allowance revokes nothing, and stops new tokens")
    void lower() {
        service.setGranted(GUILD, account, premium, 1);
        for (int i = 0; i < 11; i++) created("server " + i);

        assertTrue(service.setGranted(GUILD, account, premium, 0));

        assertEquals(11, service.tokens(account).size());
        assertEquals(
                Refusal.LIMIT_REACHED,
                service.create(account, premium, "one more").refusal());
    }

    @Test
    @DisplayName("Release types come from the account's access")
    void releaseTypes() {
        assertEquals(Set.of("STABLE"), service.releaseTypes(account, premium));
        assertEquals(Set.of(), service.releaseTypes(account, unrelated));
    }

    @Test
    @DisplayName("A product the account lost access to still shows while it holds tokens for it")
    void allowancesKeepHeldProducts() {
        created("lobby");
        accountRoles.replaceDiscordRoles(account, GUILD, List.of());

        assertEquals(List.of(new UpdateTokenService.Allowance(premium, 1, 10)), service.allowances(account));
    }
}
