/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.configuration.TestConf;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.trial.service.DisposableEmailDomains;
import de.chojo.lyna.feature.trial.service.WebTrialService;
import de.chojo.lyna.feature.trial.service.WebTrialService.Reason;
import de.chojo.lyna.gateway.Gateway;
import de.chojo.lyna.repository.RepositoryTestBase;
import de.chojo.nexus.NexusRest;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who may take a product's trial on the web: by Discord when the bot sees the member, by their Lyna
 * account otherwise, and once per person whichever way. The guild's trial settings are the defaults:
 * thirty minutes in the guild, thirty days of account age.
 */
class WebTrialServiceTest extends RepositoryTestBase {
    private static final long GUILD = 4601L;
    private static final long DISCORD = 7001L;

    private final Gateway gateway = mock(Gateway.class);
    private Guilds guilds;
    private Product trialProduct;

    @BeforeEach
    void seed() throws SQLException {
        clear("trial", "trial_settings", "product", "account_email", "account_identity", "account");
        guilds = new Guilds(mock(NexusRest.class), TestConf.defaults(), accountLinks);
        trialProduct = guilds.guild(GUILD)
                .products()
                .create("Tried", 9L, null, false, true)
                .orElseThrow();
        when(gateway.member(Mockito.anyLong(), Mockito.anyLong())).thenReturn(Optional.empty());
    }

    private WebTrialService at(Instant now) {
        return new WebTrialService(
                productRepository,
                accountService,
                accountEmails,
                accountLinks,
                gateway,
                new DisposableEmailDomains(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private int accountWithEmail(String address) {
        int account = accounts.insert(null).id();
        accountEmails.add(account, address);
        accountEmails.verify(account, address);
        return account;
    }

    private static Instant monthsLater() {
        return Instant.now().plus(Duration.ofDays(31));
    }

    @Test
    @DisplayName("A product without a trial, or a free one, offers none")
    void notOffered() {
        Product free = guilds.guild(GUILD)
                .products()
                .create("Free", 9L, null, true, true)
                .orElseThrow();
        Product none = guilds.guild(GUILD)
                .products()
                .create("None", 9L, null, false, false)
                .orElseThrow();
        int account = accountWithEmail("a@example.org");

        assertEquals(Reason.NOT_OFFERED, at(monthsLater()).decide(free, account).reason());
        assertEquals(Reason.NOT_OFFERED, at(monthsLater()).decide(none, account).reason());
    }

    @Test
    @DisplayName(
            "A Lyna account must be old enough, with a proved address that is not a throwaway one, and takes it once")
    void byAccount() {
        int account = accounts.insert(null).id();

        var young = at(Instant.now()).decide(trialProduct, account);
        assertEquals(Reason.ACCOUNT_TOO_NEW, young.reason());
        assertTrue(young.remaining().toDays() >= 29);

        assertEquals(
                Reason.NO_VERIFIED_EMAIL,
                at(monthsLater()).decide(trialProduct, account).reason());
        accountEmails.add(account, "a@mailinator.com");
        accountEmails.verify(account, "a@mailinator.com");
        assertEquals(
                Reason.DISPOSABLE_EMAIL,
                at(monthsLater()).decide(trialProduct, account).reason());
        accountEmails.add(account, "a@example.org");
        accountEmails.verify(account, "a@example.org");

        WebTrialService trials = at(monthsLater());
        assertTrue(trials.decide(trialProduct, account).eligible());
        trials.spend(trialProduct, account);
        assertEquals(Reason.SPENT, trials.decide(trialProduct, account).reason());
    }

    @Test
    @DisplayName(
            "A member the bot sees is held to the Discord rules: long enough in the guild, Discord account old enough")
    void byDiscord() {
        int account = accounts.insert(null).id();
        accountLinks.link(account, DISCORD, AccountIdentity.Verification.OAUTH);
        Member member = mock(Member.class);
        User user = mock(User.class);
        when(member.getUser()).thenReturn(user);
        when(gateway.member(GUILD, DISCORD)).thenReturn(Optional.of(member));
        Instant now = Instant.parse("2026-06-01T12:00:00Z");

        when(member.getTimeJoined())
                .thenReturn(OffsetDateTime.ofInstant(now.minus(Duration.ofMinutes(5)), ZoneOffset.UTC));
        when(user.getTimeCreated())
                .thenReturn(OffsetDateTime.ofInstant(now.minus(Duration.ofDays(400)), ZoneOffset.UTC));
        assertEquals(
                Reason.GUILD_MEMBERSHIP_TOO_NEW,
                at(now).decide(trialProduct, account).reason());

        when(member.getTimeJoined())
                .thenReturn(OffsetDateTime.ofInstant(now.minus(Duration.ofDays(2)), ZoneOffset.UTC));
        when(user.getTimeCreated()).thenReturn(OffsetDateTime.ofInstant(now.minus(Duration.ofDays(3)), ZoneOffset.UTC));
        assertEquals(
                Reason.DISCORD_ACCOUNT_TOO_NEW,
                at(now).decide(trialProduct, account).reason());

        when(user.getTimeCreated())
                .thenReturn(OffsetDateTime.ofInstant(now.minus(Duration.ofDays(400)), ZoneOffset.UTC));
        assertTrue(at(now).decide(trialProduct, account).eligible(), "no proved address is needed on this path");
    }

    @Test
    @DisplayName("A trial taken on Discord counts for the web, so linking afterwards gives no second one")
    void discordTrialCounts() {
        int account = accountWithEmail("b@example.org");
        accountLinks.link(account, DISCORD, AccountIdentity.Verification.OAUTH);
        productRepository.spendTrial(trialProduct.id(), DISCORD);

        assertEquals(
                Reason.SPENT, at(monthsLater()).decide(trialProduct, account).reason());
    }
}
