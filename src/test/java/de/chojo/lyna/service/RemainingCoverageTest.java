/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.instance.entity.InstanceSettings;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.repository.RepositoryTestBase;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The corners the other suites do not reach: trials, the storefront's own reads, the instance
 * settings row, and an address confirmed on an account that already has one.
 */
class RemainingCoverageTest extends RepositoryTestBase {
    private static final long GUILD = 4501L;
    private static final long MEMBER = 8001L;

    private int productId;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "trial",
                "download",
                "download_type",
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "product",
                "account_email",
                "account_identity",
                "account");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("""
                    INSERT INTO %s.product (guild_id, name, role, free)
                    VALUES (%d, 'Widget', 5, TRUE) RETURNING id
                    """.formatted(schemaName, GUILD))) {
                rows.next();
                productId = rows.getInt(1);
            }
        }
    }

    private Product productOf() {
        var guilds = new de.chojo.lyna.feature.guild.Guilds(
                Mockito.mock(de.chojo.nexus.NexusRest.class),
                de.chojo.lyna.configuration.TestConf.defaults(),
                accountLinks);
        return guilds.guild(GUILD).products().byId(productId).orElseThrow();
    }

    private Member member() {
        Guild guild = Mockito.mock(Guild.class);
        Mockito.when(guild.getIdLong()).thenReturn(GUILD);
        Member member = Mockito.mock(Member.class);
        Mockito.when(member.getIdLong()).thenReturn(MEMBER);
        Mockito.when(member.getGuild()).thenReturn(guild);
        Mockito.when(member.getRoles()).thenReturn(List.of());
        return member;
    }

    @Test
    @DisplayName("A free product is open to anyone, without asking the database")
    void freeProductsAreOpen() {
        Product product = productOf();
        Member member = member();

        assertTrue(productRoles.canAccess(product, member));
        assertTrue(productRoles.canDownload(product, member));
        assertEquals(
                java.util.Set.of(de.chojo.lyna.feature.download.entity.ReleaseType.values())
                        .size(),
                productRoles.availableReleaseTypes(product, member).size(),
                "a free product offers every release");
    }

    @Test
    @DisplayName("A paid product is shut to somebody holding nothing")
    void paidProductsAreShut() throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("UPDATE %s.product SET free = FALSE WHERE id = %d".formatted(schemaName, productId));
        }
        Product product = productOf();
        Member member = member();

        assertTrue(productRoles.availableReleaseTypes(product, member).isEmpty());
        assertFalse(productRoles.canDownload(product, member));
    }

    @Test
    @DisplayName("A trial is there until it is spent, and then it is not")
    void trialsAreSpent() {
        Product product = productOf();
        Member member = member();

        assertTrue(trials.hasTrial(product, member));
        trials.claimTrial(product, member);
        assertFalse(trials.hasTrial(product, member));
    }

    @Test
    @DisplayName("The storefront lists products, says which are free, and takes an icon")
    void theStorefrontReads() {
        assertTrue(kioskProducts.isFree(productId));
        kioskProducts.iconUrl(productId, "https://example.invalid/icon.png");

        var listed = kioskProducts.all();
        assertEquals(1, listed.size());
        assertEquals("https://example.invalid/icon.png", listed.get(0).iconUrl());
        assertTrue(listed.get(0).free());
    }

    @Test
    @DisplayName("The instance settings row is read and written back")
    void instanceSettingsRoundTrip() {
        InstanceSettings current = instanceSettings.get();
        assertEquals("transistor", current.defaultTheme(), "the default the schema ships");

        instanceSettings.update(new InstanceSettings("workbench", false, List.of("workbench"), null));
        InstanceSettings next = instanceSettings.get();
        assertEquals("workbench", next.defaultTheme());
        assertFalse(next.allowUserTheme());
    }

    @Test
    @DisplayName("The first address an account proves becomes the one it is written to")
    void theFirstProvedAddressBecomesPrimary() {
        Account account = accountService.register(null, "hash");
        assertTrue(accountEmails.primary(account.id()).isEmpty(), "nothing to write to yet");

        accountEmailService.confirm(account.id(), "only@example.invalid");

        assertEquals(
                "only@example.invalid",
                accountEmails.primary(account.id()).orElseThrow().email(),
                "an account nothing can be sent to is one nobody can be reached at");
    }

    @Test
    @DisplayName("The themes an instance offers survive the round trip")
    void enabledThemesRoundTrip() {
        instanceSettings.update(new InstanceSettings("workbench", true, List.of("workbench", "transistor"), null));

        assertEquals(List.of("workbench", "transistor"), instanceSettings.get().enabledThemes());
    }

    @Test
    @DisplayName("What a held licence opens for one product is read back")
    void releaseTypesForAProduct() throws SQLException {
        Account account = accountService.register("holder@example.invalid", "hash");
        int licenseId;
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("""
                    INSERT INTO %s.license (product_id, user_identifier, key)
                    VALUES (%d, 'holder@example.invalid', 'REL-KEY') RETURNING id
                    """.formatted(schemaName, productId))) {
                rows.next();
                licenseId = rows.getInt(1);
            }
            statement.execute("INSERT INTO %s.user_license (account_id, license_id) VALUES (%d, %d)"
                    .formatted(schemaName, account.id(), licenseId));
            statement.execute("INSERT INTO %s.license_access (license_id, release_type) VALUES (%d, 'STABLE')"
                    .formatted(schemaName, licenseId));
        }

        assertEquals(java.util.Set.of("STABLE"), accountLicenses.releaseTypes(account.id(), productId));
        assertTrue(accountLicenses.entitledProductIds(account.id()).contains(productId));
    }

    @Test
    @DisplayName("Confirming a second address leaves the first as the one written to")
    void confirmingASecondAddress() {
        Account account = accountService.register("first@example.invalid", "hash");
        accountEmailService.confirm(account.id(), "first@example.invalid");

        accountEmailService.confirm(account.id(), "second@example.invalid");

        assertEquals(2, accountEmails.of(account.id()).size());
        assertEquals(
                "first@example.invalid",
                accountEmails.primary(account.id()).orElseThrow().email(),
                "the first one stays the one it is written to");
    }
}
