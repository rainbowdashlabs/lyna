package de.chojo.lyna.mail;

import de.chojo.jdautil.configuration.Configuration;
import de.chojo.jdautil.consumer.ThrowingConsumer;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.configuration.elements.Mailing;
import de.chojo.lyna.core.Data;
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
    private final Data data;
    private final Configuration<ConfigFile> configuration;
    private static final Logger log = getLogger(MailingService.class);
    private final List<ThrowingConsumer<Message, Exception>> receivedListener = new ArrayList<>();

    public MailingService(Threading threading, Data data, Configuration<ConfigFile> configuration) {
        this.threading = threading;
        this.data = data;
        this.configuration = configuration;
    }

    public static MailingService create(Threading threading, Data data, Configuration<ConfigFile> configuration) {
        MailingService mailingService = new MailingService(threading, data, configuration);
        while (true) {
            try {
                mailingService.init();
            } catch (MessagingException e) {
                log.error(LogNotify.NOTIFY_ADMIN, "Could not connect to mail", e);
                continue;
            }
            break;
        }
        return mailingService;
    }

    private void init() throws MessagingException {
        threading.botWorker().scheduleAtFixedRate(this::loop, 10, configuration.config().mailing().pollSeconds(), TimeUnit.SECONDS);
        registerMessageListener(new MailHandler(data, this, configuration));
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

    private Session createSession() {
        log.debug("Creating new mail session");
        Properties props = System.getProperties();
        Mailing mailing = configuration.config().mailing();
        props.putAll(mailing.properties());
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(mailing.user(), mailing.password());
            }
        });
    }

    public void registerMessageListener(ThrowingConsumer<Message, Exception> listener) {
        receivedListener.add(listener);
    }


    public void sendMail(Mail mail) {
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
        Transport.send(message, configuration.config().mailing().user(), configuration.config().mailing().password());
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
        message.addFrom(new Address[]{new InternetAddress(configuration.config().mailing().user())});
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(mail.address(), false));
        message.setDataHandler(new DataHandler(mail.text(), "text/html; charset=UTF-8"));
        message.setSubject(mail.subject());
        message.setHeader("X-Mailer", "Lyna");
        message.setSentDate(new Date());
        return message;
    }
}
