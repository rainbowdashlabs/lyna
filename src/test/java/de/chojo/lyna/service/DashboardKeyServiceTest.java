/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.download.service.DownloadAccessService;
import de.chojo.lyna.feature.updater.entity.DashboardSettings;
import de.chojo.lyna.feature.updater.entity.KeyKind;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.repository.DashboardProductRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenQuotaRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenRepository;
import de.chojo.lyna.feature.updater.service.DashboardKeyService;
import de.chojo.lyna.feature.updater.service.DashboardKeyService.Refreshed;
import de.chojo.lyna.feature.updater.service.DashboardKeyService.Verified;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dashboard keys: who may hold how many, refreshing them on their schedule, the grace after a
 * rotation, expiry, and what the gateway is told when it verifies one.
 *
 * <p>Time is moved the way it passes in the database: a key's timestamps are set back, rather than
 * the service's clock set forward, so the rows the service reads are the rows it would read days
 * later. The clock is moved only to look at the moments around a retirement or a refresh.
 */
class DashboardKeyServiceTest extends RepositoryTestBase {
    private static final long GUILD = 5201L;
    private static final long ROLE = 9201L;

    private UpdateTokenService service;
    private DashboardKeyService keys;
    private int buyer;
    private int trier;
    private int stranger;
    private int companies;
    private int switchedOff;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "update_token_request",
                "update_token_quota",
                "update_token_retired",
                "update_token",
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
        companies = product("Companies", true);
        switchedOff = product("Quiet", false);
        for (int product : List.of(companies, switchedOff)) {
            execute("INSERT INTO %%s.role_access (role_id, product_id, release_type) VALUES (%d, %d, 'STABLE')"
                    .formatted(ROLE, product));
            execute("INSERT INTO %%s.trial (product_id, account_id) VALUES (%d, %d)".formatted(product, trier));
        }
        accountRoles.replaceDiscordRoles(buyer, GUILD, List.of(ROLE));
    }

    private UpdateTokenService service(Clock clock) {
        return new UpdateTokenService(
                new UpdateTokenRepository(),
                new UpdateTokenQuotaRepository(),
                new DashboardProductRepository(),
                new DownloadAccessService(accountLicenses, accountRoles, productRepository),
                kioskProducts,
                clock);
    }

    private DashboardKeyService keys(Clock clock) {
        return new DashboardKeyService(new UpdateTokenRepository(), service, clock);
    }

    private static Clock in(Duration offset) {
        return Clock.fixed(Instant.now().plus(offset), ZoneOffset.UTC);
    }

    private int product(String name, boolean dashboard) throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "INSERT INTO %s.product (guild_id, name, role, dashboard_keys) VALUES (%d, '%s', 5, %b) RETURNING id"
                                .formatted(schemaName, GUILD, name, dashboard))) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private UpdateToken created(int account, String name) {
        return service.create(account, companies, KeyKind.DASHBOARD, name).value();
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
    @DisplayName("A buyer may hold the product's allowance of dashboard keys, prefixed as such")
    void buyerAllowance() {
        UpdateToken key = created(buyer, "lobby");

        assertTrue(key.token().startsWith("lyd_"));
        assertEquals(KeyKind.DASHBOARD, key.kind());
        assertEquals(
                List.of(new Allowance(companies, KeyKind.DASHBOARD, 1, 10, false)),
                service.allowances(buyer, KeyKind.DASHBOARD).stream()
                        .filter(allowance -> allowance.productId() == companies)
                        .toList());
        for (int i = 1; i < 10; i++) created(buyer, "server " + i);
        assertEquals(
                Refusal.LIMIT_REACHED,
                service.create(buyer, companies, KeyKind.DASHBOARD, "eleven").refusal());

        var request = service.requestMore(buyer, companies, KeyKind.DASHBOARD, 2, "a network");
        assertTrue(request.ok());
        assertEquals(KeyKind.DASHBOARD, request.value().kind());
        assertEquals(List.of(request.value()), service.pending(GUILD, KeyKind.DASHBOARD));
        assertEquals(List.of(), service.pending(GUILD, KeyKind.UPDATE));
        service.decide(GUILD, request.value().id(), true, null);

        assertEquals(12, service.allowed(buyer, companies, KeyKind.DASHBOARD));
        assertEquals(10, service.allowed(buyer, companies, KeyKind.UPDATE), "the other kind is untouched");
        assertEquals(1, service.raised(GUILD, KeyKind.DASHBOARD).size());
        assertTrue(service.create(buyer, companies, KeyKind.DASHBOARD, "eleven").ok());
    }

    @Test
    @DisplayName("The allowance follows the product's setting")
    void allowanceSetting() {
        assertTrue(service.configureDashboard(GUILD, companies, new DashboardSettings(true, 7, 30, 2))
                .ok());
        created(buyer, "one");
        created(buyer, "two");

        assertEquals(
                Refusal.LIMIT_REACHED,
                service.create(buyer, companies, KeyKind.DASHBOARD, "three").refusal());
        assertTrue(service.setGranted(GUILD, buyer, companies, KeyKind.DASHBOARD, 1));
        assertTrue(service.create(buyer, companies, KeyKind.DASHBOARD, "three").ok());
    }

    @Test
    @DisplayName("A trial account holds exactly one key, cannot ask for more, and gets no upgrade tokens")
    void trialAllowance() {
        assertEquals(Holding.TRIAL, service.holding(trier, companies));
        assertEquals(
                List.of(new Allowance(companies, KeyKind.DASHBOARD, 0, 1, true)),
                service.allowances(trier, KeyKind.DASHBOARD));

        assertTrue(created(trier, "lobby").token().startsWith("lyd_"));

        assertEquals(
                List.of(new Allowance(companies, KeyKind.DASHBOARD, 1, 1, true)),
                service.allowances(trier, KeyKind.DASHBOARD));
        assertEquals(
                Refusal.LIMIT_REACHED,
                service.create(trier, companies, KeyKind.DASHBOARD, "second").refusal());
        assertEquals(
                Refusal.NO_ACCESS,
                service.requestMore(trier, companies, KeyKind.DASHBOARD, 1, "please")
                        .refusal());
        assertEquals(
                Refusal.NO_ACCESS,
                service.create(trier, companies, KeyKind.UPDATE, "updater").refusal());
        assertEquals(List.of(), service.allowances(trier, KeyKind.UPDATE));
    }

    @Test
    @DisplayName("A trial taken on Discord counts for the account linked to it")
    void discordTrial() throws SQLException {
        execute(("INSERT INTO %%s.account_identity (provider, external_id, account_id, verified_via)"
                        + " VALUES ('discord', '31337', %d, 'oauth')")
                .formatted(stranger));
        execute("INSERT INTO %%s.trial (product_id, user_id) VALUES (%d, 31337)".formatted(companies));

        assertEquals(Holding.TRIAL, service.holding(stranger, companies));
        assertEquals(1, service.allowed(stranger, companies, KeyKind.DASHBOARD));
    }

    @Test
    @DisplayName("Nobody gets a key without the product or its trial, nor for a product that offers none")
    void refusedCreation() {
        assertEquals(
                Refusal.NO_ACCESS,
                service.create(stranger, companies, KeyKind.DASHBOARD, "lobby").refusal());
        assertEquals(0, service.allowed(stranger, companies, KeyKind.DASHBOARD));
        assertEquals(List.of(), service.allowances(stranger, KeyKind.DASHBOARD));
        assertEquals(
                Refusal.NOT_OFFERED,
                service.create(buyer, switchedOff, KeyKind.DASHBOARD, "lobby").refusal());
        assertEquals(
                Refusal.NOT_OFFERED,
                service.requestMore(buyer, switchedOff, KeyKind.DASHBOARD, 1, "why")
                        .refusal());
        assertEquals(
                Refusal.INVALID_NAME,
                service.create(buyer, companies, KeyKind.DASHBOARD, " ").refusal());
        assertTrue(service.allowances(buyer, KeyKind.DASHBOARD).stream()
                .noneMatch(allowance -> allowance.productId() == switchedOff));
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
        assertTrue(keys.verify(key.token()).ok());
        assertTrue(keys.refresh(key.token()).ok(), "a verification does not hold a refresh off");

        assertEquals(Refusal.TOO_SOON, keys.refresh(key.token()).refusal());
        assertTrue(keys(in(DashboardKeyService.MIN_REFRESH_INTERVAL.plusSeconds(1)))
                .refresh(key.token())
                .ok());
    }

    @Test
    @DisplayName("Once seven days have passed, a refresh trades the key for its successor")
    void refreshWhenDue() throws SQLException {
        UpdateToken key = created(buyer, "lobby");
        age(key, Duration.ofDays(7).plusMinutes(1));

        Refreshed refreshed = keys.refresh(key.token()).value();

        assertTrue(refreshed.rotated());
        UpdateToken next = refreshed.entry();
        assertNotEquals(key.token(), next.token());
        assertTrue(next.token().startsWith("lyd_"));
        assertEquals(key.id(), next.id());
        assertTrue(next.refreshAt().isAfter(Instant.now().plus(Duration.ofDays(6))));
        assertTrue(keys(in(Duration.ofMinutes(1))).refresh(next.token()).ok());
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
        Verified verified = keys(in(Duration.ofMinutes(9))).verify(key.token()).value();
        assertTrue(verified.rotated());
        assertEquals(key.id(), verified.entry().id());
    }

    @Test
    @DisplayName("After the grace a retired key is refused and its reuse recorded")
    void afterGrace() throws SQLException {
        UpdateToken key = created(buyer, "lobby");
        age(key, Duration.ofDays(8));
        keys.refresh(key.token());
        ageRetired(key.token(), DashboardKeyService.GRACE.plusSeconds(1));

        assertEquals(Refusal.REUSED, keys.refresh(key.token()).refusal());
        assertEquals(Refusal.REUSED, keys.verify(key.token()).refusal());
        assertNotNull(service.tokens(buyer).getFirst().lastReuseAt());
    }

    @Test
    @DisplayName("A key unused for thirty days expires, and a reissue brings its entry back")
    void expiry() throws SQLException {
        UpdateToken key = created(buyer, "lobby");
        keys.verify(key.token());
        age(key, Duration.ofDays(29));
        assertTrue(keys.verify(key.token()).ok(), "used within the thirty days");

        age(key, Duration.ofDays(30).plusMinutes(1));

        assertEquals(Refusal.EXPIRED, keys.refresh(key.token()).refusal());
        assertEquals(Refusal.EXPIRED, keys.verify(key.token()).refusal());

        UpdateToken reissued = service.reissue(buyer, key.id()).value();
        assertTrue(reissued.token().startsWith("lyd_"));
        assertTrue(keys.verify(reissued.token()).ok());
    }

    @Test
    @DisplayName("Losing the product refuses the key without ending it, and getting it back revives it")
    void accessLostAndBack() {
        UpdateToken key = created(buyer, "lobby");
        accountRoles.replaceDiscordRoles(buyer, GUILD, List.of());

        assertEquals(Refusal.NO_ACCESS, keys.refresh(key.token()).refusal());
        assertEquals(Refusal.NO_ACCESS, keys.verify(key.token()).refusal());
        assertEquals(1, service.tokens(buyer).size());
        assertEquals(1, service.allowances(buyer, KeyKind.DASHBOARD).size(), "still listed while held");

        accountRoles.replaceDiscordRoles(buyer, GUILD, List.of(ROLE));
        assertTrue(keys.verify(key.token()).ok());
    }

    @Test
    @DisplayName("Switching a product's dashboard keys off refuses every key at its next use")
    void switchedOffProduct() {
        UpdateToken key = created(buyer, "lobby");
        assertTrue(service.configureDashboard(GUILD, companies, new DashboardSettings(false, 7, 30, 10))
                .ok());

        assertEquals(Refusal.NOT_OFFERED, keys.verify(key.token()).refusal());
        assertEquals(Refusal.NOT_OFFERED, keys.refresh(key.token()).refusal());
    }

    @Test
    @DisplayName("Settings out of range or for another guild's product are refused")
    void configureRefused() {
        assertEquals(
                Refusal.INVALID_REQUEST,
                service.configureDashboard(GUILD, companies, new DashboardSettings(true, 0, 30, 10))
                        .refusal());
        assertEquals(
                Refusal.INVALID_REQUEST,
                service.configureDashboard(GUILD, companies, new DashboardSettings(true, 7, 30, -1))
                        .refusal());
        assertEquals(
                Refusal.NOT_FOUND,
                service.configureDashboard(GUILD + 1, companies, DashboardSettings.DEFAULT)
                        .refusal());
        assertEquals(DashboardSettings.DEFAULT, service.dashboard(-1));
    }

    @Test
    @DisplayName("A revoked key is unknown at once, by its owner's hand or a guild admin's")
    void revoke() {
        UpdateToken mine = created(buyer, "lobby");
        UpdateToken other = created(buyer, "survival");

        assertTrue(service.revoke(buyer, mine.id()));
        assertEquals(Refusal.UNKNOWN, keys.verify(mine.token()).refusal());
        assertEquals(Refusal.UNKNOWN, keys.refresh(mine.token()).refusal());

        assertEquals(1, service.held(GUILD, KeyKind.DASHBOARD).size());
        assertFalse(service.revokeInGuild(GUILD + 1, other.id(), KeyKind.DASHBOARD));
        assertTrue(service.revokeInGuild(GUILD, other.id(), KeyKind.DASHBOARD));
        assertEquals(Refusal.UNKNOWN, keys.verify(other.token()).refusal());
    }

    @Test
    @DisplayName("The gateway learns the product, whether it was bought or tried, the entry and its name")
    void verifyAnswers() {
        UpdateToken bought = created(buyer, "lobby");
        UpdateToken tried = created(trier, "home");

        Verified verified = keys.verify(bought.token()).value();

        assertEquals(Holding.BOUGHT, verified.holding());
        assertFalse(verified.rotated());
        assertEquals(companies, verified.entry().productId());
        assertEquals(bought.id(), verified.entry().id());
        assertEquals("lobby", verified.entry().name());
        assertEquals(bought.refreshAt(), verified.entry().refreshAt());
        assertNotNull(verified.entry().lastUsedAt(), "a verification is a use");
        assertEquals(Holding.TRIAL, keys.verify(tried.token()).value().holding());
    }

    @Test
    @DisplayName("A key of the wrong kind, or one nobody issued, is unknown to either use")
    void wrongKind() {
        UpdateToken token =
                service.create(buyer, companies, KeyKind.UPDATE, "updater").value();
        UpdateToken key = created(buyer, "lobby");

        assertEquals(Refusal.UNKNOWN, keys.verify(token.token()).refusal());
        assertEquals(Refusal.UNKNOWN, keys.refresh(token.token()).refusal());
        assertEquals(Refusal.UNKNOWN, keys.verify("lyd_nobody").refusal());
        assertEquals(Refusal.UNKNOWN, keys.verify(null).refusal());
        assertEquals(Refusal.UNKNOWN, service.resolve(key.token()).refusal());
        assertEquals(Refusal.UNKNOWN, service.trade(key.token()).refusal());
        assertTrue(keys.verify(key.token()).ok(), "presenting it elsewhere spent nothing");
    }
}
