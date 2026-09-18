/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.mail;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.jdautil.consumer.ThrowingConsumer;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.feature.license.entity.LicenseSource;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.mail.repository.MailingLookup;
import de.chojo.lyna.feature.purchase.service.PurchaseService;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class MailHandler implements ThrowingConsumer<Message, Exception> {
    private final LicenseService licenseService;
    private final MailingLookup mailings;
    private static final Logger log = getLogger(MailHandler.class);
    private final MailingService mailingService;
    private final PurchaseService purchases;
    private final Conf configuration;

    private final Cache<String, String> cache =
            CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    public MailHandler(
            MailingLookup mailings,
            MailingService mailingService,
            PurchaseService purchases,
            Conf configuration,
            LicenseService licenseService) {
        this.licenseService = licenseService;
        this.mailings = mailings;
        this.mailingService = mailingService;
        this.purchases = purchases;
        this.configuration = configuration;
    }

    @Override
    public void accept(Message message) throws Exception {
        de.chojo.lyna.configuration.elements.Mailing mailConf =
                configuration.main().mailing();
        InternetAddress address = (InternetAddress) message.getFrom()[0];
        if (!mailConf.skipVerify()) {
            // Check if address is from PayPal
            if (!"service@paypal.de".equals(address.getAddress())
                    && !mailConf.originMails().contains(address.getAddress())) {
                log.info("Received mail from unknown sender {}", address.getAddress());
                return;
            }
            // Verify that address was forwarded from whitelisted mail address, if it was forwarded.
            // We always accept mails from the origin address.
            String[] header = message.getHeader("X-Forwarded-For");
            if (header != null) {
                if (mailConf.originMails().isEmpty()) {
                    log.warn(
                            LogNotify.NOTIFY_ADMIN,
                            "Refused a mail forwarded by {} because mailing.originMail is empty. "
                                    + "Name the addresses that forward receipts here to accept them.",
                            header[0]);
                    return;
                }
                boolean valid = false;
                for (String mail : mailConf.originMails()) {
                    if (header[0].contains(mail)) {
                        valid = true;
                        break;
                    }
                }
                if (!valid) {
                    log.info("Invalid forwarding address {}", header[0]);
                    return;
                }
            }
        }

        var mailHtml = new MailParser(message).parsed();

        PayPalMail parsed = PayPalMail.parse(mailHtml);

        if (parsed.product().isEmpty()) {
            log.error(LogNotify.NOTIFY_ADMIN, "Could not extract product from {}!", message.getSubject());
            return;
        }

        if (parsed.name().isEmpty()) {
            log.error(LogNotify.NOTIFY_ADMIN, "Could not extract name from {}!", message.getSubject());
            return;
        }

        if (parsed.mail().isEmpty()) {
            log.error(LogNotify.NOTIFY_ADMIN, "Could not extract address from {}", message.getSubject());
            return;
        }

        // This is cursed, but the quickest workaround.
        synchronized (cache) {
            if (cache.getIfPresent(parsed.mail().get()) != null) {
                return;
            }

            cache.put(parsed.mail().get(), parsed.mail().get());
        }
        Optional<Mailing> optMailing = mailings.byName(parsed.product().get());
        if (optMailing.isEmpty()) {
            log.error(
                    LogNotify.NOTIFY_ADMIN,
                    "Could not find a matching mailing entry for {}",
                    parsed.product().get());
            return;
        }

        Mailing mailing = optMailing.get();
        purchases.issue(mailing.product(), parsed.mail().get(), parsed.name().get(), LicenseSource.MAIL);
    }
}
