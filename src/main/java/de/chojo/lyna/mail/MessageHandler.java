package de.chojo.lyna.mail;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.jdautil.configuration.Configuration;
import de.chojo.jdautil.consumer.ThrowingConsumer;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.data.access.Mailings;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.mailings.Mailing;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class MessageHandler implements ThrowingConsumer<Message, Exception> {
    private final Mailings mailings;
    private static final Logger log = getLogger(MessageHandler.class);
    private final MailingService mailingService;
    private final Configuration<ConfigFile> configuration;

    private final Cache<String, String> cache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    public MessageHandler(Data data, MailingService mailingService, Configuration<ConfigFile> configuration) {
        this.mailings = data.mailings();
        this.mailingService = mailingService;
        this.configuration = configuration;
    }

    @Override
    public void accept(Message message) throws Exception {
        de.chojo.lyna.configuration.elements.Mailing mailConf = configuration.config().mailing();
        InternetAddress address = (InternetAddress) message.getFrom()[0];
        if ("false".equalsIgnoreCase(System.getProperty("bot.mailing.skipverify", "false"))) {
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
            log.error(LogNotify.NOTIFY_ADMIN, "Could not find a matching mailing entry for {}", parsed.product().get());
            return;
        }

        Mailing mailing = optMailing.get();
        Optional<License> license = mailing.product().createLicense(parsed.mail().get());
        license.get().grantAccess(ReleaseType.STABLE);
        Mail mail = MailCreator.createLicenseMessage(mailing, license.get().key(), parsed.name().get(), parsed.mail().get());
        mailingService.sendMail(mail);
    }
}
