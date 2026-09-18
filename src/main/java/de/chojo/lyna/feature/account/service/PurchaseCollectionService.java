/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.account.entity.AccountEmail;
import de.chojo.lyna.feature.account.repository.AccountEmailRepository;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.license.repository.LicenseInviteRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * How a licence and the account it belongs to find each other.
 *
 * <p>Two ways round, and they have to agree. Proving an address collects what is already waiting on
 * it; issuing a licence hands it to somebody who proved the address earlier. Which of the two
 * happens first is not something anybody controls, so both are here and both ask the same questions.
 *
 * <p>Those questions are what make this safe, and they are the reason this is a service rather than
 * something a route or a repository does in passing: the address must have been <em>proved</em>, and
 * the licence must be one nobody holds.
 */
@Singleton
public class PurchaseCollectionService {
    private final AccountEmailRepository emails;
    private final AccountLicenseRepository licenses;
    private final LicenseInviteRepository invites;

    @Inject
    public PurchaseCollectionService(
            AccountEmailRepository emails, AccountLicenseRepository licenses, LicenseInviteRepository invites) {
        this.emails = emails;
        this.licenses = licenses;
        this.invites = invites;
    }

    /**
     * Hands the account what was waiting on an address it has just proved.
     *
     * <p>Two things arrive this way: a licence somebody invited the address onto, and a licence
     * issued against it - bought in the shop, parsed out of a payment receipt, or written down by an
     * operator. That second kind is the point of an account holding more than one address at all:
     * somebody who paid from one address and signed up with another otherwise has to carry the key
     * across by hand.
     *
     * <p>Only a licence nobody holds. One already claimed stays with whoever claimed it - proving an
     * address is a way to find a purchase, not a way to take one.
     *
     * <p>The caller is responsible for the address having been proved.
     *
     * @return the licences the account now holds because of this address
     */
    public List<Integer> collect(int accountId, String email) {
        List<Integer> collected = new ArrayList<>(invites.bind(accountId, email));
        for (int licenseId : licenses.unheldFor(email)) {
            licenses.claim(accountId, licenseId);
            collected.add(licenseId);
        }
        return collected;
    }

    /**
     * Gives a licence to whoever has already proved the address it was issued against.
     *
     * <p>An address that has merely been claimed counts as nobody: claiming one would otherwise be a
     * way to take what was bought with it, and the mail sent afterwards would tell whoever claimed it
     * that a purchase had been made.
     *
     * @return whether the licence was handed to an account, which is what the mail then says
     */
    public boolean handOver(int licenseId, String address) {
        return emails.byAddress(address)
                .filter(AccountEmail::verified)
                .map(held -> licenses.claim(held.accountId(), licenseId))
                .orElse(false);
    }
}
