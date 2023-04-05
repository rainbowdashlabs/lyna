package de.chojo.lyna.mail;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.configuration.elements.Mailing;
import de.chojo.lyna.core.Threading;
import jakarta.mail.*;
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
import java.util.function.Consumer;

import static org.slf4j.LoggerFactory.getLogger;

public class MailingService {
    private final Threading threading;
    private final Configuration<ConfigFile> configuration;
    private static final Logger log = getLogger(MailingService.class);
    private Session session;
    private Store imapStore;
    private final List<Consumer<Message>> receivedListener = new ArrayList<>();

    public MailingService(Threading threading, Configuration<ConfigFile> configuration) {
        this.threading = threading;
        this.configuration = configuration;
    }

    public static MailingService create(Threading threading, Configuration<ConfigFile> configuration) {
        MailingService mailingService = new MailingService(threading, configuration);
        try {
            mailingService.init();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
        mailingService.registerMessageListener(message -> {
            try {
                mailingService.sendMail("<b>Received c:</b>",((InternetAddress) message.getFrom()[0]).getAddress());
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        });
        return mailingService;
    }

    private void init() throws MessagingException {
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
        imapStore = session.getStore("imap");
        imapStore.connect();
        IMAPFolder inbox = (IMAPFolder) imapStore.getFolder("Inbox");

        inbox.open(Folder.READ_WRITE);
        inbox.addMessageCountListener(new MessageCountAdapter() {
            @Override
            public void messagesAdded(MessageCountEvent e) {
                if (e.getType() == MessageCountEvent.REMOVED) return;
                log.info("Received messages");
                for (Message message : e.getMessages()) {
                    for (Consumer<Message> messageConsumer : receivedListener) {
                        messageConsumer.accept(message);
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
                throw new RuntimeException(e);
            }
        }, threading.botWorker()).whenComplete((res, err) -> {
            if (err != null) {
                log.error("Could not read mails");
            } else {
                log.info("New email received");
            }
            waitForMail(folder);
        });
    }

    public void registerMessageListener(Consumer<Message> listener) {
        receivedListener.add(listener);
    }

    public void sendMail(String text, String address) {
        MimeMessage mimeMessage;
        try {
            mimeMessage = buildMessage(text, address);
        } catch (MessagingException e) {
            log.error("Could not build mail", e);
            return;
        }
        try {
            sendMessage(mimeMessage);
        } catch (MessagingException e) {
            log.error("Could not sent mail", e);
        }
    }

    private void sendMessage(MimeMessage message) throws MessagingException {
        Transport.send(message, configuration.config().mailing().user(), configuration.config().mailing().password());
        Folder sent = imapStore.getFolder("inbox").getFolder("Sent");
        if (!sent.exists()) {
            sent.create(Folder.HOLDS_MESSAGES);
        }
        sent.appendMessages(new Message[]{message});
    }

    private MimeMessage buildMessage(String text, String address) throws MessagingException {
        var message = new MimeMessage(session);
        message.addFrom(new Address[]{new InternetAddress(configuration.config().mailing().user())});
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(address, false));
        // "<b>Received c:</b>"
        message.setText(text);
        message.setSubject("Henlo");
        message.setHeader("X-Mailer", "Lyna");
        message.setSentDate(new Date());
        return message;
    }
}
