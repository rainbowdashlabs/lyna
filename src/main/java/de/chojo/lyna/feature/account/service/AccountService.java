/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.repository.AccountEmailRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;

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
}
