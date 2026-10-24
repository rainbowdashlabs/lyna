/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.download.service.DownloadAccessService;
import de.chojo.lyna.feature.updater.entity.KeyKind;
import de.chojo.lyna.feature.updater.entity.ProductKeyType;
import de.chojo.lyna.feature.updater.entity.ProductKeyType.Draft;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.repository.ProductKeyTypeRepository;
import de.chojo.lyna.feature.updater.repository.TrialHolderRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenQuotaRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenRepository;
import de.chojo.lyna.feature.updater.service.ServerKeyService;
import de.chojo.lyna.feature.updater.service.ServerKeyService.Refreshed;
import de.chojo.lyna.feature.updater.service.ServerKeyService.Verified;
import de.chojo.lyna.feature.updater.service.UpdateTokenService;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Allowance;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Holding;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Outcome;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Refusal;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server keys: who may hold how many of a key type, refreshing them on their type's schedule, the
 * grace after a rotation, expiry, which gateway a key opens, and what that gateway is told.
 *
 * <p>Time is moved the way it passes in the database: a key's timestamps are set back, rather than
 * the service's clock set forward, so the rows the service reads are the rows it would read days
 * later. The clock is moved only to look at the moments around a retirement or a refresh.
 */
class ServerKeyServiceTest extends RepositoryTestBase {
    private static final long GUILD = 5201L;
    private static final long ROLE = 9201L;
    private static final String COMMAND = "/companyadmin dashboard key {key}";

    private final ProductKeyTypeRepository keyTypes = new ProductKeyTypeRepository();
    private UpdateTokenService service;
    private ServerKeyService keys;
    private int buyer;
    private int trier;
    private int stranger;
    private int companies;
    private int chatty;
    private ProductKeyType dashboard;
    private ProductKeyType proxy;
    private ProductKeyType switchedOff;
    private ProductKeyType chattyDashboard;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "update_token_request",
                "update_token_quota",
                "update_token_retired",
                "update_token",
                "product_key_type",
                "trial",
                "account_role",
                "role_access",
                "product",
                "account_identity",
                "account");
        service = service(Clock.systemUTC());
        keys = keys(Clock.systemUTC());
        buyer = accountService.register("buyer@example.invalid", "hash").id();
        trier = accountService.register("trier@example.invalid", "hash").id();
        stranger = accountService.register("stranger@example.invalid", "hash").id();
        companies = product("Companies");
        int quiet = product("Quiet");
        chatty = product("Chatty");
        for (int product : List.of(companies, quiet, chatty)) {
            execute("INSERT INTO %%s.role_access (role_id, product_id, release_type) VALUES (%d, %d, 'STABLE')"
                    .formatted(ROLE, product));
            execute("INSERT INTO %%s.trial (product_id, account_id) VALUES (%d, %d)".formatted(product, trier));
        }
        accountRoles.replaceDiscordRoles(buyer, GUILD, List.of(ROLE));
        dashboard = keyType(companies, draft("dashboard", "Dashboard key", true));
        proxy = keyType(companies, draft("proxy", "Proxy key", true));
        switchedOff = keyType(quiet, draft("dashboard", "Dashboard key", false));
        chattyDashboard = keyType(chatty, draft("dashboard", "Dashboard key", true));
    }

    private UpdateTokenService service(Clock clock) {
        return new UpdateTokenService(
                new UpdateTokenRepository(),
                new UpdateTokenQuotaRepository(),
                new TrialHolderRepository(),
                keyTypes,
                new DownloadAccessService(accountLicenses, accountRoles, productRepository),
                kioskProducts,
                clock);
    }

    private ServerKeyService keys(Clock clock) {
        return new ServerKeyService(new UpdateTokenRepository(), keyTypes, service, clock);
    }

    private static Clock in(Duration offset) {
        return Clock.fixed(Instant.now().plus(offset), ZoneOffset.UTC);
    }

    private static Draft draft(String slug, String name, boolean enabled) {
        return new Draft(slug, name, COMMAND, enabled, 7, 30, 10, 1);
    }

    private ProductKeyType keyType(int product, Draft draft) {
        return keyTypes.create(GUILD, product, draft, "lyg_" + draft.slug() + product)
                .orElseThrow();
    }

    private void change(ProductKeyType type, Draft draft) {
        keyTypes.update(GUILD, type.id(), draft).orElseThrow();
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

    private UpdateToken created(int account, String name) {
        return service.create(account, companies, dashboard.id(), name).value();
    }

    private Allowance allowance(ProductKeyType type, int used, int allowed, boolean trial) {
        return new Allowance(
                type.productId(), KeyKind.SERVER, type.id(), type.name(), type.command(), used, allowed, trial);
    }

    private List<Allowance> allowances(int account, ProductKeyType type) {
        return service.allowances(account, KeyKind.SERVER).stream()
                .filter(allowance -> type.id() == allowance.keyTypeId())
                .toList();
    }

    /**
     * Moves a key's past back, as if the given time had passed since it was issued and last used.
     */
    private void age(UpdateToken key, Duration passed) throws SQLException {
        execute(("UPDATE %%s.update_token SET rotated_at = rotated_at - interval '%d seconds',"
                        + " created_at = created_at - interval '%1$d seconds',"
                        + " last_used_at = last_used_at - interval '%1$d seconds',"
                        + " refreshed_at = refreshed_at - interval '%1$d seconds' WHERE id = %2$d")
                .formatted(passed.toSeconds(), key.id()));
    }

    private void ageRetired(String key, Duration passed) throws SQLException {
        execute("UPDATE %%s.update_token_retired SET retired_at = retired_at - interval '%d seconds' WHERE token = '%s'"
                .formatted(passed.toSeconds(), key));
    }

    @Test
    @DisplayName("A buyer may hold the key type's allowance of server keys, prefixed as such")
    void buyerAllowance() {
        UpdateToken key = created(buyer, "lobby");

        assertTrue(key.token().startsWith("lyd_"));
        assertEquals(KeyKind.SERVER, key.kind());
        assertEquals(dashboard.id(), key.keyTypeId());
        assertEquals(List.of(allowance(dashboard, 1, 10, false)), allowances(buyer, dashboard));
        assertEquals(List.of(allowance(proxy, 0, 10, false)), allowances(buyer, proxy), "every type on its own");
        for (int i = 1; i < 10; i++) created(buyer, "server " + i);
        assertEquals(
                Refusal.LIMIT_REACHED,
                service.create(buyer, companies, dashboard.id(), "eleven").refusal());
        assertTrue(service.create(buyer, companies, proxy.id(), "eleven").ok(), "another type has its own");

        var request = service.requestMore(buyer, companies, dashboard.id(), 2, "a network");
        assertTrue(request.ok());
        assertEquals(KeyKind.SERVER, request.value().kind());
        assertEquals(dashboard.id(), request.value().keyTypeId());
        assertEquals(List.of(request.value()), service.pending(GUILD, KeyKind.SERVER));
        assertEquals(List.of(), service.pending(GUILD, KeyKind.UPDATE));
        service.decide(GUILD, request.value().id(), true, null);

        assertEquals(12, service.allowed(buyer, companies, dashboard.id()));
        assertEquals(10, service.allowed(buyer, companies, proxy.id()), "the other type is untouched");
        assertEquals(10, service.allowed(buyer, companies, null), "upgrade tokens are untouched");
        assertEquals(1, service.raised(GUILD, KeyKind.SERVER).size());
        assertTrue(service.create(buyer, companies, dashboard.id(), "eleven").ok());
    }

    @Test
    @DisplayName("The allowance follows the key type's setting")
    void allowanceSetting() {
        change(dashboard, new Draft("dashboard", "Dashboard key", COMMAND, true, 7, 30, 2, 1));
        created(buyer, "one");
        created(buyer, "two");

        assertEquals(
                Refusal.LIMIT_REACHED,
                service.create(buyer, companies, dashboard.id(), "three").refusal());
        assertTrue(service.setGranted(GUILD, buyer, companies, dashboard.id(), 1));
        assertTrue(service.create(buyer, companies, dashboard.id(), "three").ok());
        assertFalse(service.setGranted(GUILD, buyer, companies, chattyDashboard.id(), 1), "another product's key type");
    }

    @Test
    @DisplayName(
            "A trial account holds the key type's trial allowance, cannot ask for more, and gets no upgrade tokens")
    void trialAllowance() {
        assertEquals(Holding.TRIAL, service.holding(trier, companies));
        assertEquals(List.of(allowance(dashboard, 0, 1, true)), allowances(trier, dashboard));

        assertTrue(created(trier, "lobby").token().startsWith("lyd_"));

        assertEquals(List.of(allowance(dashboard, 1, 1, true)), allowances(trier, dashboard));
        assertEquals(
                Refusal.LIMIT_REACHED,
                service.create(trier, companies, dashboard.id(), "second").refusal());
        assertEquals(
                Refusal.NO_ACCESS,
                service.requestMore(trier, companies, dashboard.id(), 1, "please")
                        .refusal());
        assertEquals(
                Refusal.NO_ACCESS,
                service.create(trier, companies, null, "updater").refusal());
        assertEquals(List.of(), service.allowances(trier, KeyKind.UPDATE));

        change(dashboard, new Draft("dashboard", "Dashboard key", COMMAND, true, 7, 30, 10, 2));
        assertEquals(2, service.allowed(trier, companies, dashboard.id()));
        assertTrue(service.create(trier, companies, dashboard.id(), "second").ok());
    }

    @Test
    @DisplayName("A trial taken on Discord counts for the account linked to it")
    void discordTrial() throws SQLException {
        execute(("INSERT INTO %%s.account_identity (provider, external_id, account_id, verified_via)"
                        + " VALUES ('discord', '31337', %d, 'oauth')")
                .formatted(stranger));
        execute("INSERT INTO %%s.trial (product_id, user_id) VALUES (%d, 31337)".formatted(companies));

        assertEquals(Holding.TRIAL, service.holding(stranger, companies));
        assertEquals(1, service.allowed(stranger, companies, dashboard.id()));
    }

    @Test
    @DisplayName("Nobody gets a key without the product or its trial, nor of a type switched off or not the product's")
    void refusedCreation() {
        assertEquals(
                Refusal.NO_ACCESS,
                service.create(stranger, companies, dashboard.id(), "lobby").refusal());
        assertEquals(0, service.allowed(stranger, companies, dashboard.id()));
        assertEquals(List.of(), service.allowances(stranger, KeyKind.SERVER));
        assertEquals(
                Refusal.NOT_OFFERED,
                service.create(buyer, switchedOff.productId(), switchedOff.id(), "lobby")
                        .refusal());
        assertEquals(
                Refusal.NOT_OFFERED,
                service.requestMore(buyer, switchedOff.productId(), switchedOff.id(), 1, "why")
                        .refusal());
        assertEquals(
                Refusal.NOT_OFFERED,
                service.create(buyer, companies, chattyDashboard.id(), "lobby").refusal(),
                "a key type of another product");
        assertEquals(
                Refusal.NOT_OFFERED,
                service.create(buyer, companies, dashboard.id() + 1000, "lobby").refusal());
        assertEquals(0, service.allowed(buyer, companies, chattyDashboard.id()));
        assertEquals(Optional.empty(), service.keyType(companies, chattyDashboard.id()));
        assertEquals(Optional.of(dashboard), service.keyType(companies, dashboard.id()));
        assertEquals(
                Refusal.INVALID_NAME,
                service.create(buyer, companies, dashboard.id(), " ").refusal());
        assertTrue(allowances(buyer, switchedOff).isEmpty());
    }

    @Test
    @DisplayName("Before the refresh interval has passed, a refresh answers the same key and notes the use")
    void refreshBeforeDue() {
        UpdateToken key = created(buyer, "lobby");

        Outcome<Refreshed> refreshed = keys.refresh(key.token());

        assertTrue(refreshed.ok());
        assertFalse(refreshed.value().rotated());
        assertEquals(key.token(), refreshed.value().entry().token());
        assertEquals(key.refreshAt(), refreshed.value().entry().refreshAt());
        assertNotNull(refreshed.value().entry().lastUsedAt());
        assertNotNull(refreshed.value().entry().refreshedAt());
    }

    @Test
    @DisplayName("A refresh moments after the last is too soon; a verification in between is not a refresh")
    void tooSoon() {
        UpdateToken key = created(buyer, "lobby");
        assertTrue(keys.verify(dashboard, key.token()).ok());
        assertTrue(keys.refresh(key.token()).ok(), "a verification does not hold a refresh off");

        assertEquals(Refusal.TOO_SOON, keys.refresh(key.token()).refusal());
        assertTrue(keys(in(ServerKeyService.MIN_REFRESH_INTERVAL.plusSeconds(1)))
                .refresh(key.token())
                .ok());
    }

    @Test
    @DisplayName("Once the key type's refresh interval has passed, a refresh trades the key for its successor")
    void refreshWhenDue() throws SQLException {
        UpdateToken key = created(buyer, "lobby");
        age(key, Duration.ofDays(7).plusMinutes(1));

        Refreshed refreshed = keys.refresh(key.token()).value();

        assertTrue(refreshed.rotated());
        UpdateToken next = refreshed.entry();
        assertNotEquals(key.token(), next.token());
        assertTrue(next.token().startsWith("lyd_"));
        assertEquals(key.id(), next.id());
        assertEquals(dashboard.id(), next.keyTypeId());
        assertTrue(next.refreshAt().isAfter(Instant.now().plus(Duration.ofDays(6))));
        assertTrue(keys(in(Duration.ofMinutes(1))).refresh(next.token()).ok());
    }

    @Test
    @DisplayName("A shorter refresh interval on the key type makes a key due sooner")
    void refreshFollowsKeyType() throws SQLException {
        UpdateToken key = created(buyer, "lobby");
        age(key, Duration.ofDays(2).plusMinutes(1));
        assertFalse(keys.refresh(key.token()).value().rotated());

        change(dashboard, new Draft("dashboard", "Dashboard key", COMMAND, true, 2, 30, 10, 1));

        assertTrue(keys(in(ServerKeyService.MIN_REFRESH_INTERVAL.plusSeconds(1)))
                .refresh(key.token())
                .value()
                .rotated());
    }

    @Test
    @DisplayName("Of two refreshes of a due key at the same moment one rotates, and both answer its successor")
    void rotationRace() throws Exception {
        UpdateToken key = created(buyer, "lobby");
        age(key, Duration.ofDays(8));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> keys.refresh(key.token()), executor);
            var second = CompletableFuture.supplyAsync(() -> keys.refresh(key.token()), executor);
            Outcome<Refreshed> a = first.get();
            Outcome<Refreshed> b = second.get();

            assertTrue(a.ok());
            assertTrue(b.ok());
            assertEquals(a.value().entry().token(), b.value().entry().token());
            assertNotEquals(key.token(), a.value().entry().token());
        }
        assertEquals(1, countRows("update_token_retired"));
    }

    @Test
    @DisplayName("A retired key inside the grace is answered with the current key, without rotating again")
    void grace() throws SQLException {
        UpdateToken key = created(buyer, "lobby");
        age(key, Duration.ofDays(8));
        UpdateToken next = keys.refresh(key.token()).value().entry();

        Outcome<Refreshed> again = keys(in(Duration.ofMinutes(9))).refresh(key.token());

        assertTrue(again.ok());
        assertTrue(again.value().rotated());
        assertEquals(next.token(), again.value().entry().token());
        assertEquals(1, countRows("update_token_retired"));
        Verified verified =
                keys(in(Duration.ofMinutes(9))).verify(dashboard, key.token()).value();
        assertTrue(verified.rotated());
        assertEquals(key.id(), verified.entry().id());
        assertEquals(
                Refusal.UNKNOWN,
                keys(in(Duration.ofMinutes(9))).verify(proxy, key.token()).refusal(),
                "inside the grace still only for its own type");
    }

    @Test
    @DisplayName("After the grace a retired key is refused and its reuse recorded")
    void afterGrace() throws SQLException {
        UpdateToken key = created(buyer, "lobby");
        age(key, Duration.ofDays(8));
        keys.refresh(key.token());
        ageRetired(key.token(), ServerKeyService.GRACE.plusSeconds(1));

        assertEquals(Refusal.REUSED, keys.refresh(key.token()).refusal());
        assertEquals(Refusal.REUSED, keys.verify(dashboard, key.token()).refusal());
        assertNotNull(service.tokens(buyer).getFirst().lastReuseAt());
    }

    @Test
    @DisplayName("A key unused for the key type's expiry interval expires, and a reissue brings its entry back")
    void expiry() throws SQLException {
        UpdateToken key = created(buyer, "lobby");
        keys.verify(dashboard, key.token());
        age(key, Duration.ofDays(29));
        assertTrue(keys.verify(dashboard, key.token()).ok(), "used within the thirty days");

        age(key, Duration.ofDays(30).plusMinutes(1));

        assertEquals(Refusal.EXPIRED, keys.refresh(key.token()).refusal());
        assertEquals(Refusal.EXPIRED, keys.verify(dashboard, key.token()).refusal());

        UpdateToken reissued = service.reissue(buyer, key.id()).value();
        assertTrue(reissued.token().startsWith("lyd_"));
        assertTrue(keys.verify(dashboard, reissued.token()).ok());
    }

    @Test
    @DisplayName("Losing the product refuses the key without ending it, and getting it back revives it")
    void accessLostAndBack() {
        UpdateToken key = created(buyer, "lobby");
        accountRoles.replaceDiscordRoles(buyer, GUILD, List.of());

        assertEquals(Refusal.NO_ACCESS, keys.refresh(key.token()).refusal());
        assertEquals(Refusal.NO_ACCESS, keys.verify(dashboard, key.token()).refusal());
        assertEquals(1, service.tokens(buyer).size());
        assertEquals(
                List.of(allowance(dashboard, 1, 0, false)),
                service.allowances(buyer, KeyKind.SERVER),
                "still listed while held");

        accountRoles.replaceDiscordRoles(buyer, GUILD, List.of(ROLE));
        assertTrue(keys.verify(dashboard, key.token()).ok());
    }

    @Test
    @DisplayName("Switching a key type off refuses every key of it at its next use, and leaves it listed")
    void switchedOffKeyType() {
        UpdateToken key = created(buyer, "lobby");
        UpdateToken other =
                service.create(buyer, companies, proxy.id(), "proxy").value();
        change(dashboard, draft("dashboard", "Dashboard key", false));

        assertEquals(Refusal.NOT_OFFERED, keys.verify(dashboard, key.token()).refusal());
        assertEquals(Refusal.NOT_OFFERED, keys.refresh(key.token()).refusal());
        assertTrue(keys.verify(proxy, other.token()).ok(), "the product's other type is unaffected");
        assertEquals(1, allowances(buyer, dashboard).size(), "still listed while held");
    }

    @Test
    @DisplayName("Deleting a key type ends its keys at once")
    void deletedKeyType() {
        UpdateToken key = created(buyer, "lobby");

        assertTrue(keyTypes.delete(GUILD, dashboard.id()));

        assertEquals(Refusal.UNKNOWN, keys.verify(dashboard, key.token()).refusal());
        assertEquals(Refusal.UNKNOWN, keys.refresh(key.token()).refusal());
        assertTrue(allowances(buyer, dashboard).isEmpty());
    }

    @Test
    @DisplayName("A revoked key is unknown at once, by its owner's hand or a guild admin's")
    void revoke() {
        UpdateToken mine = created(buyer, "lobby");
        UpdateToken other = created(buyer, "survival");

        assertTrue(service.revoke(buyer, mine.id()));
        assertEquals(Refusal.UNKNOWN, keys.verify(dashboard, mine.token()).refusal());
        assertEquals(Refusal.UNKNOWN, keys.refresh(mine.token()).refusal());

        assertEquals(1, service.held(GUILD, KeyKind.SERVER).size());
        assertFalse(service.revokeInGuild(GUILD + 1, other.id(), KeyKind.SERVER));
        assertFalse(service.revokeInGuild(GUILD, other.id(), KeyKind.UPDATE));
        assertTrue(service.revokeInGuild(GUILD, other.id(), KeyKind.SERVER));
        assertEquals(Refusal.UNKNOWN, keys.verify(dashboard, other.token()).refusal());
    }

    @Test
    @DisplayName("The gateway learns the key type, whether the product was bought or tried, the entry and its name")
    void verifyAnswers() {
        UpdateToken bought = created(buyer, "lobby");
        UpdateToken tried = created(trier, "home");

        Verified verified = keys.verify(dashboard, bought.token()).value();

        assertEquals(Holding.BOUGHT, verified.holding());
        assertEquals(dashboard, verified.type());
        assertFalse(verified.rotated());
        assertEquals(companies, verified.entry().productId());
        assertEquals(bought.id(), verified.entry().id());
        assertEquals("lobby", verified.entry().name());
        assertEquals(bought.refreshAt(), verified.entry().refreshAt());
        assertNotNull(verified.entry().lastUsedAt(), "a verification is a use");
        assertEquals(
                Holding.TRIAL, keys.verify(dashboard, tried.token()).value().holding());
    }

    @Test
    @DisplayName("A key of one type is unknown to the gateway of another product's type, and untouched by it")
    void otherProductsGateway() {
        UpdateToken key = created(buyer, "lobby");

        assertEquals(Refusal.UNKNOWN, keys.verify(chattyDashboard, key.token()).refusal());
        assertNull(service.tokens(buyer).getFirst().lastUsedAt(), "asking the wrong gateway is no use");

        UpdateToken chattyKey =
                service.create(buyer, chatty, chattyDashboard.id(), "lobby").value();
        assertEquals(Refusal.UNKNOWN, keys.verify(dashboard, chattyKey.token()).refusal());
        assertTrue(keys.verify(chattyDashboard, chattyKey.token()).ok());
        assertTrue(keys.verify(dashboard, key.token()).ok());
    }

    @Test
    @DisplayName("A key of one type is unknown to the gateway of another type of the same product")
    void sameProductsOtherType() {
        UpdateToken key = created(buyer, "lobby");
        UpdateToken proxyKey =
                service.create(buyer, companies, proxy.id(), "proxy").value();

        assertEquals(Refusal.UNKNOWN, keys.verify(proxy, key.token()).refusal());
        assertEquals(Refusal.UNKNOWN, keys.verify(dashboard, proxyKey.token()).refusal());
        assertTrue(keys.verify(dashboard, key.token()).ok());
        assertTrue(keys.verify(proxy, proxyKey.token()).ok());
    }

    @Test
    @DisplayName("A gateway secret finds its own key type, and nothing else does")
    void gatewaySecret() {
        assertEquals(Optional.of(dashboard), keys.gateway(dashboard.gatewaySecret()));
        assertEquals(Optional.of(proxy), keys.gateway(proxy.gatewaySecret()));
        assertEquals(Optional.of(chattyDashboard), keys.gateway(chattyDashboard.gatewaySecret()));
        assertEquals(Optional.empty(), keys.gateway(null));
        assertEquals(Optional.empty(), keys.gateway(""));
        assertEquals(Optional.empty(), keys.gateway("lyg_nobody"));
        assertEquals(Optional.empty(), keys.gateway(dashboard.gatewaySecret().substring(4)), "without its prefix");
        assertEquals(
                Optional.empty(),
                keys.gateway("lyd_" + dashboard.gatewaySecret().substring(4)));
        UpdateToken key = created(buyer, "lobby");
        assertEquals(Optional.empty(), keys.gateway(key.token()), "a server key is no secret");
    }

    @Test
    @DisplayName("A key of the wrong kind, or one nobody issued, is unknown to either use")
    void wrongKind() {
        UpdateToken token = service.create(buyer, companies, null, "updater").value();
        UpdateToken key = created(buyer, "lobby");

        assertEquals(Refusal.UNKNOWN, keys.verify(dashboard, token.token()).refusal());
        assertEquals(Refusal.UNKNOWN, keys.refresh(token.token()).refusal());
        assertEquals(Refusal.UNKNOWN, keys.verify(dashboard, "lyd_nobody").refusal());
        assertEquals(Refusal.UNKNOWN, keys.verify(dashboard, null).refusal());
        assertEquals(Refusal.UNKNOWN, service.resolve(key.token()).refusal());
        assertEquals(Refusal.UNKNOWN, service.trade(key.token()).refusal());
        assertTrue(keys.verify(dashboard, key.token()).ok(), "presenting it elsewhere spent nothing");
    }
}
