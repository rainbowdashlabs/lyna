/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.trial.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.entity.AccountEmail;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.repository.AccountEmailRepository;
import de.chojo.lyna.feature.account.service.AccountLinkService;
import de.chojo.lyna.feature.account.service.AccountService;
import de.chojo.lyna.feature.guild.entity.TrialSettings;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductRepository;
import de.chojo.lyna.gateway.Gateway;
import net.dv8tion.jda.api.entities.Member;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Who may take a product's trial on the web.
 *
 * <p>Somebody whose linked Discord account the bot sees in the product's guild is held to the
 * {@code /trial} rules: in the guild long enough, with a Discord account old enough. Anybody else is
 * held to their Lyna account: old enough, with an address they proved that is not from a throwaway
 * provider - the closest thing to "this is a person" a bare account offers.
 *
 * <p>One trial per person per product, whichever way it was taken: the account and its Discord id are
 * both recorded, so linking Discord afterwards does not open a second one.
 */
@Singleton
public class WebTrialService {
    private final ProductRepository products;
    private final AccountService accounts;
    private final AccountEmailRepository emails;
    private final AccountLinkService links;
    private final Gateway gateway;
    private final DisposableEmailDomains disposable;
    private final Clock clock;

    public enum Reason {
        ELIGIBLE,
        /** The product offers no trial, or is free anyway. */
        NOT_OFFERED,
        SPENT,
        GUILD_MEMBERSHIP_TOO_NEW,
        DISCORD_ACCOUNT_TOO_NEW,
        ACCOUNT_TOO_NEW,
        NO_VERIFIED_EMAIL,
        DISPOSABLE_EMAIL
    }

    /**
     * @param remaining how long until a refusal for age passes, or zero
     */
    public record Decision(Reason reason, Duration remaining) {
        public boolean eligible() {
            return reason == Reason.ELIGIBLE;
        }

        static Decision of(Reason reason) {
            return new Decision(reason, Duration.ZERO);
        }
    }

    @Inject
    public WebTrialService(
            ProductRepository products,
            AccountService accounts,
            AccountEmailRepository emails,
            AccountLinkService links,
            Gateway gateway,
            DisposableEmailDomains disposable) {
        this(products, accounts, emails, links, gateway, disposable, Clock.systemUTC());
    }

    /**
     * @param clock what "now" is, which a test sets
     */
    public WebTrialService(
            ProductRepository products,
            AccountService accounts,
            AccountEmailRepository emails,
            AccountLinkService links,
            Gateway gateway,
            DisposableEmailDomains disposable,
            Clock clock) {
        this.products = products;
        this.accounts = accounts;
        this.emails = emails;
        this.links = links;
        this.gateway = gateway;
        this.disposable = disposable;
        this.clock = clock;
    }

    public Decision decide(Product product, int accountId) {
        if (!product.trial() || product.free()) return Decision.of(Reason.NOT_OFFERED);
        Long discordId = discordId(accountId);
        if (!products.trialUnspent(product.id(), accountId, discordId)) return Decision.of(Reason.SPENT);
        TrialSettings settings = product.products().licenseGuild().settings().trial();
        Optional<Member> member = discordId == null ? Optional.empty() : gateway.member(product.guildId(), discordId);
        return member.map(found -> byDiscord(found, settings)).orElseGet(() -> byAccount(accountId, settings));
    }

    /**
     * Marks the trial taken, for the account and for its Discord id when it has one.
     */
    public void spend(Product product, int accountId) {
        products.spendTrial(product.id(), accountId, discordId(accountId));
    }

    private Decision byDiscord(Member member, TrialSettings settings) {
        Duration inGuild = remaining(member.getTimeJoined().toInstant(), settings.serverTime());
        if (!inGuild.isZero()) return new Decision(Reason.GUILD_MEMBERSHIP_TOO_NEW, inGuild);
        Duration discordAge = remaining(member.getUser().getTimeCreated().toInstant(), settings.accountTime());
        if (!discordAge.isZero()) return new Decision(Reason.DISCORD_ACCOUNT_TOO_NEW, discordAge);
        return Decision.of(Reason.ELIGIBLE);
    }

    private Decision byAccount(int accountId, TrialSettings settings) {
        Optional<Account> account = accounts.findById(accountId);
        if (account.isEmpty()) return Decision.of(Reason.NO_VERIFIED_EMAIL);
        Duration age = remaining(account.get().createdAt(), settings.accountTime());
        if (!age.isZero()) return new Decision(Reason.ACCOUNT_TOO_NEW, age);
        var proved =
                emails.of(accountId).stream().filter(AccountEmail::verified).toList();
        if (proved.isEmpty()) return Decision.of(Reason.NO_VERIFIED_EMAIL);
        if (proved.stream().allMatch(email -> disposable.isDisposable(email.email()))) {
            return Decision.of(Reason.DISPOSABLE_EMAIL);
        }
        return Decision.of(Reason.ELIGIBLE);
    }

    private Duration remaining(Instant since, Duration required) {
        Duration left = Duration.between(clock.instant(), since.plus(required));
        return left.isNegative() ? Duration.ZERO : left;
    }

    private Long discordId(int accountId) {
        return links.discordIdentity(accountId)
                .map(AccountIdentity::externalIdAsLong)
                .orElse(null);
    }
}
