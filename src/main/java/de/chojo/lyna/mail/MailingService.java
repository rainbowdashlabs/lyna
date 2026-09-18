package de.chojo.lyna.mail;

import de.chojo.jdautil.consumer.ThrowingConsumer;
import com.google.inject.Inject;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.elements.Mailing;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.access.Mailings;
import de.chojo.lyna.core.Threading;
import de.chojo.lyna.util.Retry;
import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class MailingService {
    private final Threading threading;
    private final Mailings mailings;
    private final Accounts accounts;
    private final Conf configuration;
    private static final Logger log = getLogger(MailingService.class);
    private final List<ThrowingConsumer<Message, Exception>> receivedListener = new ArrayList<>();
    private final MailTemplateRenderer renderer;

    @Inject
    public MailingService(Threading threading, Mailings mailings, Accounts accounts, Conf configuration) {
        this.threading = threading;
        this.mailings = mailings;
        this.accounts = accounts;
        this.configuration = configuration;
        this.renderer = new MailTemplateRenderer(configuration.main().mailing().senderName(),
                configuration.main().links().frontend());
    }

    /**
     * Builds the service and, where mail is configured, starts polling for it.
     *
     * <p>A failure to start the polling is reported and left at that rather than retried until it
     * succeeds. Retrying here holds up the rest of the startup, so a mailbox that is briefly away
     * used to take the bot and the HTTP API down with it; the scheduled poll recovers on its own
     * once the mailbox answers again.
     *
     * @return the service, polling unless mail is switched off
     */
    public void start() {
        if (!configuration.main().mailing().enabled()) {
            log.info("Mailing is disabled. No mail is polled or sent.");
            return;
        }
        try {
            init();
        } catch (MessagingException e) {
            log.error(LogNotify.NOTIFY_ADMIN, "Could not connect to mail", e);
        }
    }

    private void init() throws MessagingException {
        threading.botWorker().scheduleAtFixedRate(this::loop, 10, configuration.main().mailing().pollSeconds(), TimeUnit.SECONDS);
        registerMessageListener(new MailHandler(mailings, this, accounts, configuration));
    }

    private void loop() {
        try {
            check();
        } catch (Exception e) {
            log.error("Could not check emails", e);
            // c:
        }
    }

    private void check() throws Exception {
        log.debug("Performing mail check");
        Session session = createSession();
        var store = (IMAPStore) createImapStore(session);
        IMAPFolder inbox = getInbox(store);
        Message[] search = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

        for (Message message : search) {
            log.info("Received new message from {}", ((InternetAddress) message.getFrom()[0]).getAddress());
            for (ThrowingConsumer<Message, Exception> consumer : receivedListener) {
                try {
                    consumer.accept(message);
                } catch (Exception ex) {
                    log.error("Error when handling mail", ex);
                }
            }
            message.setFlag(Flags.Flag.SEEN, true);
        }

        log.debug("Mail check done");
    }

    private IMAPStore createImapStore(Session session) {
        log.debug("Creating imap store");
        IMAPStore imapStore = null;
        try {
            imapStore = (IMAPStore) session.getStore("imap");
            imapStore.connect();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
        return imapStore;
    }

    /**
     * Builds the mail session from the system properties with the configured mail settings over them.
     *
     * <p>The system properties are copied rather than written into. `System.getProperties()` hands
     * back the live table, so adding the mail settings to it published host, port and protocol
     * choice to the whole JVM and let anything else holding a session be reconfigured underneath it.
     */
    private Session createSession() {
        log.debug("Creating new mail session");
        Properties props = new Properties();
        props.putAll(System.getProperties());
        Mailing mailing = configuration.main().mailing();
        props.putAll(mailing.properties());
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(mailing.user(), mailing.password());
            }
        });
    }

    /**
     * The renderer every mail goes through, so that a caller building one never has to know where
     * the templates are.
     */
    public MailTemplateRenderer renderer() {
        return renderer;
    }

    public void registerMessageListener(ThrowingConsumer<Message, Exception> listener) {
        receivedListener.add(listener);
    }


    public void send(String to, String subject, String body) {
        sendMail(new Mail(to, subject, body));
    }

    public void sendMail(Mail mail) {
        if (!configuration.main().mailing().enabled()) {
            log.info("Mailing is disabled. Dropping mail to {} with subject {}", mail.address(), mail.subject());
            return;
        }
        Session session = createSession();
        MimeMessage mimeMessage;
        try {
            mimeMessage = buildMessage(session, mail);
        } catch (MessagingException e) {
            log.error(LogNotify.NOTIFY_ADMIN, "Could not build mail", e);
            return;
        }

        Optional<Boolean> sendResult = Retry.retryAndReturn(3,
                () -> sendMessage(mimeMessage),
                err -> {
                    log.error(LogNotify.NOTIFY_ADMIN, "Could not sent mail", err);
                    sendMail(mail);
                });

        if (sendResult.isEmpty()) {
            log.error(LogNotify.NOTIFY_ADMIN, "Retries exceeded. Aborting.");
            return;
        }

        try (IMAPStore imapStore = createImapStore(session)) {
            Optional<Boolean> result = Retry.retryAndReturn(3,
                    () -> storeMessage(imapStore, mimeMessage),
                    err -> {
                        log.error(LogNotify.NOTIFY_ADMIN, "Could not store mail");
                        sendMail(mail);
                    });

            if (result.isPresent() && result.get()) {
                log.debug("Mail stored");
            } else {
                log.error(LogNotify.NOTIFY_ADMIN, "Retries exceeded. Aborting.");
            }
        } catch (MessagingException e) {
            log.error("Error occurred while sending a mail", e);
        }
    }

    private boolean sendMessage(MimeMessage message) throws MessagingException {
        log.info("Sending mail to {}", ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        Transport.send(message, configuration.main().mailing().user(), configuration.main().mailing().password());
        log.info("Mail sent.");
        return true;
    }

    private boolean storeMessage(IMAPStore store, MimeMessage message) throws MessagingException {
        store.getFolder("inbox");
        Folder sent = getInbox(store).getFolder("Sent");
        if (!sent.exists()) {
            sent.create(Folder.HOLDS_MESSAGES);
        }
        sent.appendMessages(new Message[]{message});
        return true;
    }

    private IMAPFolder getInbox(IMAPStore store) {
        return getFolder(store, "inbox");
    }

    private IMAPFolder getFolder(IMAPStore store, String name) {
        return Retry.retryAndReturn(3, () -> {
            log.debug("Connecting to folder {}", name);
            IMAPFolder folder = (IMAPFolder) store.getFolder(name);
            folder.open(Folder.READ_WRITE);
            return folder;
        }, err -> {
            log.error(LogNotify.NOTIFY_ADMIN, "Could not connect to folder. Retrying.");
            getFolder(store, name);
        }).orElseThrow(() -> new RuntimeException("Reconnecting to folder failed."));
    }

    private MimeMessage buildMessage(Session session, Mail mail) throws MessagingException {
        var message = new MimeMessage(session);
        message.addFrom(new Address[]{new InternetAddress(configuration.main().mailing().user())});
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(mail.address(), false));
        message.setDataHandler(new DataHandler(mail.text(), "text/html; charset=UTF-8"));
        message.setSubject(mail.subject());
        message.setHeader("X-Mailer", "Lyna");
        message.setSentDate(new Date());
        return message;
    }
}
