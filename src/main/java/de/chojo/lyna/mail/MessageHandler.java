package de.chojo.lyna.mail;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.jdautil.consumer.ThrowingConsumer;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.data.access.Mailings;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.mailings.Mailing;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class MessageHandler implements ThrowingConsumer<Message, Exception> {
    private final Mailings mailings;
    private static final Logger log = getLogger(MessageHandler.class);
    private final MailingService mailingService;
    private final Configuration<ConfigFile> configuration;

    public MessageHandler(Data data, MailingService mailingService, Configuration<ConfigFile> configuration) {
        this.mailings = data.mailings();
        this.mailingService = mailingService;
        this.configuration = configuration;
    }

    @Override
    public void accept(Message message) throws Exception {
        InternetAddress address = (InternetAddress) message.getFrom()[0];
        if (!"service@paypal.de".equals(address.getAddress()) && "false".equalsIgnoreCase(System.getProperty("bot.verifypaypal", "false"))) {
            log.info("Received mail from unknown sender {}", address.getAddress());
            return;
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
            log.error(LogNotify.NOTIFY_ADMIN, "Could not extract mail from {}", message.getSubject());
            return;
        }

        Optional<Mailing> optMailing = mailings.byName(parsed.product().get());
        if (optMailing.isEmpty()) {
            log.error(LogNotify.NOTIFY_ADMIN, "Could not find a matching mailing entry for {}", parsed.product().get());
            return;
        }

        Mailing mailing = optMailing.get();
        Optional<License> license = mailing.product().products().licenseGuild().licenses()
                .create(configuration.config().license().baseSeed(), mailing.product(), mailing.platform(), parsed.mail().get());
        String html = mailing.mailText();
        html = html.replace("{{ key }}", license.get().key());
        html = html.replace("{{ name }}", parsed.name().get());
        mailingService.sendMail(html, "Thank you for purchasing " + mailing.product().name(), parsed.mail().get());
    }

}
