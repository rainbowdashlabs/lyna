/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.purchase.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.account.service.PurchaseCollectionService;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.entity.LicenseSource;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.mail.Mail;
import de.chojo.lyna.mail.MailCreator;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.mail.PurchaseRecipient;

import java.util.Optional;

/**
 * Somebody buys a product and ends up holding a licence for it.
 *
 * <p>Three things arrive at this: a Ko-fi shop order, a payment receipt parsed out of the mailbox,
 * and an operator issuing one by hand. They differ in where the address comes from and in nothing
 * else, so the steps live here rather than three times over.
 *
 * <p>The steps are: mint the licence, grant it the stable release, give it to whoever has already
 * proved the address, and tell the buyer which of those two happened.
 */
@Singleton
public class PurchaseService {
    private final LicenseService licenses;
    private final PurchaseCollectionService collection;
    private final MailingService mailing;

    @Inject
    public PurchaseService(LicenseService licenses, PurchaseCollectionService collection, MailingService mailing) {
        this.licenses = licenses;
        this.collection = collection;
        this.mailing = mailing;
    }

    /**
     * Issues a licence for a purchase and tells the buyer.
     *
     * @param product the product bought
     * @param address the address it was paid from, which is what the licence is issued against
     * @param name    who bought it, as they gave it
     * @param source  where the purchase came from
     * @return the licence, or nothing when the product has no mail to send or already has a licence
     *         for that address
     */
    public Optional<License> issue(Product product, String address, String name, LicenseSource source) {
        Optional<Mailing> mailTemplate = product.mailings().get();
        if (mailTemplate.isEmpty()) return Optional.empty();
        Optional<License> license = product.createLicense(address, source);
        if (license.isEmpty()) return Optional.empty();
        licenses.grantAccess(license.get(), ReleaseType.STABLE);
        announce(mailTemplate.get(), license.get(), name, address);
        return license;
    }

    /**
     * Gives an existing licence to whoever proved the address, and sends the mail that says so.
     *
     * <p>Separate from {@link #issue} because an operator issuing a licence by hand has already made
     * it by the time this matters.
     */
    public void announce(Mailing mailTemplate, License license, String name, String address) {
        boolean handedOver = collection.handOver(license.id(), address);
        Mail mail = MailCreator.createLicenseMessage(
                mailing.renderer(),
                mailTemplate,
                license.key(),
                name,
                address,
                mailTemplate.product().url(),
                handedOver ? PurchaseRecipient.WITH_ACCOUNT : PurchaseRecipient.WITHOUT_ACCOUNT);
        mailing.sendMail(mail);
    }
}
