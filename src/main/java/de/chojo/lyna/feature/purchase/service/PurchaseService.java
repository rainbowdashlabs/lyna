/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.purchase.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.feature.account.service.PurchaseCollectionService;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.entity.LicenseSource;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.mail.service.PurchaseMailService;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.mail.Mail;
import de.chojo.lyna.mail.MailCreator;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.mail.PurchaseRecipient;
import org.slf4j.Logger;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

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
    private static final Logger log = getLogger(PurchaseService.class);

    private final LicenseService licenses;
    private final PurchaseCollectionService collection;
    private final MailingService mailing;
    private final PurchaseMailService purchaseMail;

    @Inject
    public PurchaseService(
            LicenseService licenses,
            PurchaseCollectionService collection,
            MailingService mailing,
            PurchaseMailService purchaseMail) {
        this.licenses = licenses;
        this.collection = collection;
        this.mailing = mailing;
        this.purchaseMail = purchaseMail;
    }

    /**
     * Issues a licence for a purchase and tells the buyer.
     *
     * <p>A product with no mail set still issues the licence, and still hands it to whoever proved the
     * address: the sale was made, and somebody who paid holds what they paid for. Only the mail is
     * skipped, and an operator is told so, since the buyer otherwise hears nothing.
     *
     * @param product the product bought
     * @param address the address it was paid from, which is what the licence is issued against
     * @param name    who bought it, as they gave it
     * @param source  where the purchase came from
     * @return the licence, or nothing when the product already has a licence for that address
     */
    public Optional<License> issue(Product product, String address, String name, LicenseSource source) {
        Optional<License> license = product.createLicense(address, source);
        if (license.isEmpty()) return Optional.empty();
        licenses.grantAccess(license.get(), ReleaseType.STABLE);
        Optional<Mailing> mailTemplate = product.mailings().get();
        if (mailTemplate.isEmpty()) {
            collection.handOver(license.get().id(), address);
            log.warn(
                    LogNotify.NOTIFY_ADMIN,
                    "No mail is set for product {} ({}), so the license {} issued for a {} purchase was not mailed",
                    product.name(),
                    product.id(),
                    license.get().id(),
                    source);
            return license;
        }
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
                purchaseMail.blocksFor(mailTemplate),
                mailTemplate.mailText(),
                purchaseMail.values(mailTemplate.product(), name, license.key()),
                address,
                handedOver ? PurchaseRecipient.WITH_ACCOUNT : PurchaseRecipient.WITHOUT_ACCOUNT);
        mailing.sendMail(mail);
    }
}
