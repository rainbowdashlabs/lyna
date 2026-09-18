/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.account.repository.AccountEmailRepository;

import java.util.List;

/**
 * The addresses an account is known by.
 */
@Singleton
public class AccountEmailService {
    private final AccountEmailRepository emails;
    private final PurchaseCollectionService purchases;

    @Inject
    public AccountEmailService(AccountEmailRepository emails, PurchaseCollectionService purchases) {
        this.emails = emails;
        this.purchases = purchases;
    }

    /**
     * Records that an account has proved an address is theirs, and hands it what was waiting there.
     *
     * <p>The collecting happens here rather than at the call site, so that every way of proving an
     * address lets somebody onto the licences waiting for them.
     *
     * <p>The first address an account holds is the one it is written to, which the repository decides
     * as it adds it.
     *
     * @return the licences the account was let onto by proving this address
     */
    public List<Integer> confirm(int accountId, String email) {
        emails.add(accountId, email);
        emails.verify(accountId, email);
        return purchases.collect(accountId, email);
    }
}
