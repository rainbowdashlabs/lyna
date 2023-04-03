package de.chojo.lyna.mail;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.configuration.elements.Mailing;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.event.MessageCountAdapter;
import org.eclipse.angus.mail.imap.IMAPFolder;

import java.util.Properties;

public class Mails {

    private final Configuration<ConfigFile> configuration;

    public Mails(Configuration<ConfigFile> configuration) {
        this.configuration = configuration;
    }

    private void setupMailMonitor() throws MessagingException {
        Properties props = System.getProperties();
        Session session = Session.getInstance(props, null);
        Store imapStore = session.getStore("imap");
        Mailing mailing = configuration.config().mailing();
        imapStore.connect(mailing.host(), mailing.user(), mailing.password());
        IMAPFolder inbox = (IMAPFolder) imapStore.getFolder("Inbox");

        inbox.open(Folder.READ_WRITE);
        inbox.addMessageCountListener(new MessageCountAdapter() {
            @Override
            public void messagesAdded(jakarta.mail.event.MessageCountEvent e) {
                for (Message message : e.getMessages()) {
                    try {
                        System.out.printf("%s%n", (Object) message.getFrom());
                    } catch (MessagingException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        });

        while (true) {
            try {
                inbox.idle();
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
