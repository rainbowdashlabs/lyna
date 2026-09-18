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
import de.chojo.lyna.feature.account.repository.AccountEmailRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;

import java.util.Optional;

/**
 * Making an account and the decisions that go with it.
 */
@Singleton
public class AccountService {
    private final AccountRepository accounts;
    private final AccountEmailRepository emails;

    @Inject
    public AccountService(AccountRepository accounts, AccountEmailRepository emails) {
        this.accounts = accounts;
        this.emails = emails;
    }

    /**
     * Creates an account, claiming an address for it if one was given.
     *
     * <p>An address somebody has already proved, or that another account is written to, is refused:
     * those are the two ways a person can be found, so they name one account each. An address merely
     * claimed by somebody else is not refused, because claiming one is not owning it.
     *
     * @throws IllegalStateException if the address already belongs to somebody
     */
    public Account register(String email, String passwordHash) {
        if (email == null || email.isBlank()) {
            return accounts.insert(passwordHash);
        }
        if (emails.byAddress(email).isPresent()) {
            throw new IllegalStateException("That address belongs to another account");
        }
        return accounts.insertWithPrimaryEmail(passwordHash, email);
    }

    public Optional<Account> findById(int accountId) {
        return accounts.findById(accountId);
    }

    /**
     * @return the account somebody signs in as with that address, if one does
     */
    public Optional<Account> findByEmail(String email) {
        return accounts.findByEmail(email);
    }

    public Optional<Account> findByDiscordId(long discordUserId) {
        return accounts.findByIdentity(AccountIdentity.DISCORD, Long.toString(discordUserId));
    }

    /**
     * @param displayName a name as it is shown, with or without its digits
     */
    public Optional<Account> findByUsername(String displayName) {
        return accounts.findByUsername(displayName);
    }

    public void touchLastLogin(int accountId) {
        accounts.touchLastLogin(accountId);
    }

    public void setPasswordHash(int accountId, String passwordHash) {
        accounts.setPasswordHash(accountId, passwordHash);
    }

    /**
     * A null leaves that choice to the operator's default rather than pinning it.
     */
    public void setAppearance(int accountId, String theme, String darkMode) {
        accounts.setAppearance(accountId, theme, darkMode);
    }

    public void delete(int accountId) {
        accounts.delete(accountId);
    }
}
