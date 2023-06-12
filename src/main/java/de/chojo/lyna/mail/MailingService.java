package de.chojo.lyna.mail;

import de.chojo.jdautil.configuratino.Configuration;
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
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
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
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

public class MailingService {
    private final Threading threading;
    private final Data data;
    private final Configuration<ConfigFile> configuration;
    private static final Logger log = getLogger(MailingService.class);
    private volatile Session session;
    private Store imapStore;
    private final List<ThrowingConsumer<Message, Exception>> receivedListener = new ArrayList<>();
    private MessageCountAdapter countAdapter;

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
        createMailListener();
        startMailMonitor();
        registerMessageListener(new MessageHandler(data, this, configuration));
    }

    private void createMailListener() {
        countAdapter = new MessageCountAdapter() {
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
        };
    }

    private void createImapStore() throws MessagingException {
        log.info(LogNotify.STATUS, "Creating imap store");
        imapStore = session.getStore("imap");
        imapStore.connect();
    }

    private void createSession() {
        log.info(LogNotify.STATUS, "Creating new mail session");
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
            log.error(LogNotify.NOTIFY_ADMIN, "Could not recreate imap store");
        }
    }

    private void startMailMonitor() {
        log.info(LogNotify.STATUS, "Starting monitoring");
        IMAPFolder inbox = getFolder("Inbox");

        try {
            inbox.open(Folder.READ_WRITE);
        } catch (MessagingException e) {
            // c:
            throw new RuntimeException(e);
        }

        inbox.removeMessageCountListener(countAdapter);
        inbox.addMessageCountListener(countAdapter);
        log.info(LogNotify.STATUS, "Registered mail listener");
        waitForMail(inbox);
    }

    private void waitForMail(IMAPFolder folder) {
        CompletableFuture.runAsync(() -> {
                    var inbox = folder;
                    while (true) {
                        try {
                            try {
                                log.info("Waiting for new mail.");
                                inbox.idle(true);
                            } catch (FolderClosedException e) {
                                log.error(LogNotify.NOTIFY_ADMIN, "Folder closed. Attempting to restart monitoring.");
                                startMailMonitor();
                                break;
                            } catch (MessagingException e) {
                                log.error(LogNotify.NOTIFY_ADMIN, "Could not start connection idling", e);
                                startMailMonitor();
                                break;
                            }
                        } catch (Exception e) {
                            log.error(LogNotify.NOTIFY_ADMIN, "Connection to folder failed.", e);
                            continue;
                        }
                        log.info("New email received");
                    }
                }, threading.botWorker())
                .completeOnTimeout(null, 1, TimeUnit.HOURS)
                .thenRun(this::startMailMonitor);
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

        Optional<Boolean> sendResult = Retry.retryAndReturn(3,
                () -> sendMessage(mimeMessage),
                err -> {
                    log.error(LogNotify.NOTIFY_ADMIN, "Could not sent mail", err);
                    createSession();
                });

        if (sendResult.isEmpty()) {
            log.error(LogNotify.NOTIFY_ADMIN, "Retries exceeded. Aborting.");
            return;
        }

        Optional<Boolean> result = Retry.retryAndReturn(3,
                () -> storeMessage(mimeMessage),
                err -> {
                    log.error(LogNotify.NOTIFY_ADMIN, "Could not store mail");
                    createSession();
                });

        if (result.isPresent() && result.get()) {
            log.info(LogNotify.STATUS, "Mail stored");
        } else {
            log.error(LogNotify.NOTIFY_ADMIN, "Retries exceeded. Aborting.");
        }
    }

    private boolean sendMessage(MimeMessage message) throws MessagingException {
        log.info(LogNotify.STATUS, "Sending mail to {}", ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        Transport.send(message, configuration.config().mailing().user(), configuration.config().mailing().password());
        log.info(LogNotify.STATUS, "Mail sent.");
        return true;
    }

    private boolean storeMessage(MimeMessage message) throws MessagingException {
        Folder sent = getFolder("inbox").getFolder("Sent");
        if (!sent.exists()) {
            sent.create(Folder.HOLDS_MESSAGES);
        }
        sent.appendMessages(new Message[]{message});
        return true;
    }

    private IMAPFolder getFolder(String name) {
        return Retry.retryAndReturn(3, () -> {
            log.info(LogNotify.STATUS, "Connecting to folder {}", name);
            return (IMAPFolder) imapStore.getFolder(name);
        }, err -> {
            log.error(LogNotify.NOTIFY_ADMIN, "Could not connect to folder. Rebuilding session.");
            createSession();
        }).orElseThrow(() -> new RuntimeException("Reconnecting to folder failed."));
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
