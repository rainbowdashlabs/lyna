/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.repository.AccountRepository;

import java.util.Optional;

/**
 * Which provider accounts an account is reached by.
 *
 * <p>An identity is exclusive: it names one account here, and moving it is refused rather than
 * allowed. Whoever controls a provider account could otherwise walk it onto a second account, and
 * since licences hang off accounts that would be a way to carry them across.
 */
@Singleton
public class AccountLinkService {
    private final AccountRepository accounts;
    private final UsernameService usernames;

    @Inject
    public AccountLinkService(AccountRepository accounts, UsernameService usernames) {
        this.accounts = accounts;
        this.usernames = usernames;
    }

    public void link(int accountId, long discordUserId, AccountIdentity.Verification via) {
        link(accountId, discordUserId, via, null);
    }

    public void link(int accountId, long discordUserId, AccountIdentity.Verification via, String handle) {
        link(accountId, AccountIdentity.DISCORD, Long.toString(discordUserId), via, handle);
    }

    /**
     * Links an account to an identity at some provider, and records what that provider calls it.
     *
     * @throws IllegalStateException if the identity belongs to a different account
     */
    public void link(
            int accountId, String provider, String externalId, AccountIdentity.Verification via, String handle) {
        Optional<Account> holder = accounts.findByIdentity(provider, externalId);
        if (holder.isPresent() && holder.get().id() != accountId) {
            throw new IllegalStateException(
                    "That %s identity is already linked to another account".formatted(provider));
        }
        accounts.dropOtherIdentities(accountId, provider, externalId);
        accounts.upsertIdentity(accountId, provider, externalId, via, handle);
        if (AccountIdentity.DISCORD.equals(provider)) {
            usernames.syncFromHandle(accountId, handle);
        }
    }

    /**
     * Forgets the Discord identity, which is the one the interface offers to unlink.
     */
    public void unlink(int accountId) {
        unlink(accountId, AccountIdentity.DISCORD);
    }

    /**
     * Records what a provider now calls an identity, and renames the account with it.
     *
     * <p>A rename at the provider has to reach the account, or the pages keep showing the name
     * somebody used to have.
     *
     * @return whether the handle was one the identity did not already carry
     */
    public boolean rememberHandle(long discordUserId, String handle) {
        return rememberHandle(AccountIdentity.DISCORD, Long.toString(discordUserId), handle);
    }

    public boolean rememberHandle(String provider, String externalId, String handle) {
        boolean changed = accounts.rememberHandle(provider, externalId, handle);
        if (changed && AccountIdentity.DISCORD.equals(provider)) {
            accounts.findByIdentity(provider, externalId)
                    .ifPresent(account -> usernames.syncFromHandle(account.id(), handle));
        }
        return changed;
    }

    /**
     * Forgets one provider, giving the account its own name back when that provider was naming it.
     */
    public void unlink(int accountId, String provider) {
        accounts.deleteIdentity(accountId, provider);
        if (AccountIdentity.DISCORD.equals(provider)) {
            usernames.detach(accountId);
        }
    }

    /**
     * The account a Discord id belongs to, making one if it does not have any yet.
     *
     * <p>The bot hands licences to whoever is in front of it, and most of those people have never
     * opened the web at all. Licences hang off accounts, so one is minted for them: no address, no
     * password, nothing but the identity. Signing in through Discord later lands on that same
     * account and finds the licences already there, because it is reached by the same identity.
     *
     * @return the account id, never zero
     */
    public int accountIdForDiscord(long discordUserId) {
        String externalId = Long.toString(discordUserId);
        Optional<Integer> existing = accounts.accountIdForIdentity(AccountIdentity.DISCORD, externalId);
        if (existing.isPresent()) return existing.get();

        Account created = accounts.insert(null);
        accounts.upsertIdentity(
                created.id(), AccountIdentity.DISCORD, externalId, AccountIdentity.Verification.BOT_DM_CODE, null);
        Optional<Integer> resolved = accounts.accountIdForIdentity(AccountIdentity.DISCORD, externalId);
        int id = resolved.orElseThrow(() -> new IllegalStateException("Could not link an account for " + externalId));
        if (id != created.id()) {
            accounts.delete(created.id());
        }
        return id;
    }
}
