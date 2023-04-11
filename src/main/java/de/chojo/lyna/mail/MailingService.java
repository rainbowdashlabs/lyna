package de.chojo.lyna.mail;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.jdautil.consumer.ThrowingConsumer;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.configuration.elements.Mailing;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.core.Threading;
import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.slf4j.LoggerFactory.getLogger;

public class MailingService {
    private final Threading threading;
    private final Data data;
    private final Configuration<ConfigFile> configuration;
    private static final Logger log = getLogger(MailingService.class);
    private volatile Session session;
    private Store imapStore;
    private final List<ThrowingConsumer<Message, Exception>> receivedListener = new ArrayList<>();

    public MailingService(Threading threading, Data data, Configuration<ConfigFile> configuration) {
        this.threading = threading;
        this.data = data;
        this.configuration = configuration;
    }

    public static MailingService create(Threading threading, Data data, Configuration<ConfigFile> configuration) {
        MailingService mailingService = new MailingService(threading, data, configuration);
        try {
            mailingService.init();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
        return mailingService;
    }

    private void init() throws MessagingException {
        createSession();
        createImapStore();
        startMailMonitor();
        registerMessageListener(new MessageHandler(data, this, configuration));
    }

    private void createImapStore() throws MessagingException {
        log.info("Creating imap store");
        imapStore = session.getStore("imap");
        imapStore.connect();
    }

    private void createSession() {
        log.info("Creating new mail session");
        Properties props = System.getProperties();
        Mailing mailing = configuration.config().mailing();
        props.put("mail.smtp.host", mailing.host());
        props.put("mail.imap.host", mailing.host());
        session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(mailing.user(), mailing.password());
            }
        });
        try {
            createImapStore();
        } catch (MessagingException e) {
            log.error("Could not recreate imap store");
        }
    }

    private void startMailMonitor() throws MessagingException {
        IMAPFolder inbox = (IMAPFolder) imapStore.getFolder("Inbox");

        inbox.open(Folder.READ_WRITE);
        inbox.addMessageCountListener(new MessageCountAdapter() {
            @Override
            public void messagesAdded(MessageCountEvent e) {
                if (e.getType() == MessageCountEvent.REMOVED) return;
                for (Message message : e.getMessages()) {
                    try {
                        log.info("Received new message from {}", ((InternetAddress) message.getFrom()[0]).getAddress());
                    } catch (MessagingException ex) {
                        // ignore
                    }
                    for (var messageConsumer : receivedListener) {
                        try {
                            messageConsumer.accept(message);
                        } catch (Exception ex) {
                            log.error("Error when handling mail", ex);
                        }
                    }
                }
            }
        });
        log.info("Registered mail listener");
        waitForMail(inbox);
    }

    private void waitForMail(IMAPFolder folder) {
        CompletableFuture.runAsync(() -> {
            try {
                folder.idle();
            } catch (MessagingException e) {
                log.error(LogNotify.NOTIFY_ADMIN, "Could not start connection idling", e);
                throw new RuntimeException(e);
            }
        }, threading.botWorker()).whenComplete((res, err) -> {
            if (err != null) {
                createSession();
                log.error("Could not read mails", err);
            } else {
                log.info("New email received");
            }
            waitForMail(folder);
        });
    }

    public void registerMessageListener(ThrowingConsumer<Message, Exception> listener) {
        receivedListener.add(listener);
    }


    public void sendMail(Mail mail) {
        MimeMessage mimeMessage;
        try {
            mimeMessage = buildMessage(mail);
        } catch (MessagingException e) {
            log.error(LogNotify.NOTIFY_ADMIN, "Could not build mail", e);
            return;
        }

        boolean send = false;
        var retries = 0;
        while (!send && retries < 3) {
            try {
                sendMessage(mimeMessage);
                send = true;
            } catch (MessagingException e) {
                log.error(LogNotify.NOTIFY_ADMIN, "Could not sent mail", e);
                createSession();
                retries++;
            }
        }

        if (retries == 3) {
            log.error(LogNotify.NOTIFY_ADMIN, "Retries exceeded. Aborting.");
            return;
        }

        boolean stored = false;
        retries = 0;
        while (!stored && retries < 3) {
            try {
                storeMessage(mimeMessage);
                stored = true;
            } catch (MessagingException e) {
                log.error(LogNotify.NOTIFY_ADMIN, "Could not store mail");
                createSession();
                retries++;
            }
        }
        if (retries == 3) {
            log.error(LogNotify.NOTIFY_ADMIN, "Retries exceeded. Aborting.");
        }
    }

    private void sendMessage(MimeMessage message) throws MessagingException {
        log.info("Sending mail to {}", ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        Transport.send(message, configuration.config().mailing().user(), configuration.config().mailing().password());
        log.info("Mail sent.");
    }

    private void storeMessage(MimeMessage message) throws MessagingException {
        Folder sent = imapStore.getFolder("inbox").getFolder("Sent");
        if (!sent.exists()) {
            sent.create(Folder.HOLDS_MESSAGES);
        }
        sent.appendMessages(new Message[]{message});
    }

    private MimeMessage buildMessage(Mail mail) throws MessagingException {
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
