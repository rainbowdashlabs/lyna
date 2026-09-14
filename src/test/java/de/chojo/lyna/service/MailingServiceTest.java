package de.chojo.lyna.service;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.configuration.elements.Mailing;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.core.Threading;
import de.chojo.lyna.mail.MailingService;
import jakarta.mail.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mail transport, against a real SMTP and IMAP server.
 *
 * <p>The service is built by hand rather than through {@code create(...)}, which schedules the poll
 * on the bot's executor. Here the poll is driven a step at a time, so what a test asserts is the
 * result of one check rather than of whenever the scheduler last ran.
 */
class MailingServiceTest {
    private static final String USER = "lyna@example.invalid";
    private static final String PASSWORD = "mail-password";

    private GreenMail greenMail;
    private MailingService service;

    @BeforeEach
    void startMailServer() {
        greenMail = new GreenMail(new ServerSetup[]{
                ServerSetup.SMTP.dynamicPort(),
                ServerSetup.IMAP.dynamicPort(),
        });
        greenMail.start();
        greenMail.setUser(USER, USER, PASSWORD);

        service = new MailingService(Mockito.mock(Threading.class), Mockito.mock(Data.class), configuration());
    }

    @AfterEach
    void stopMailServer() {
        greenMail.stop();
    }

    private Configuration<ConfigFile> configuration() {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", "127.0.0.1");
        properties.put("mail.smtp.port", greenMail.getSmtp().getPort());
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.imap.host", "127.0.0.1");
        properties.put("mail.imap.port", greenMail.getImap().getPort());

        Mailing mailing = Mockito.mock(Mailing.class);
        Mockito.when(mailing.user()).thenReturn(USER);
        Mockito.when(mailing.password()).thenReturn(PASSWORD);
        Mockito.when(mailing.properties()).thenReturn(properties);
        Mockito.when(mailing.pollSeconds()).thenReturn(300);
        Mockito.when(mailing.enabled()).thenReturn(true);

        ConfigFile config = Mockito.mock(ConfigFile.class);
        Mockito.when(config.mailing()).thenReturn(mailing);

        @SuppressWarnings("unchecked")
        Configuration<ConfigFile> configuration = Mockito.mock(Configuration.class);
        Mockito.when(configuration.config()).thenReturn(config);
        return configuration;
    }

    /**
     * Runs one round of the poll the scheduler would otherwise run.
     */
    private void check() throws Exception {
        Method check = MailingService.class.getDeclaredMethod("check");
        check.setAccessible(true);
        check.invoke(service);
    }

    @Test
    @DisplayName("A sent mail reaches the address it was addressed to, with its subject and body")
    void sendDeliversTheMail() throws Exception {
        service.send("buyer@example.invalid", "Your license", "<p>Here is your key</p>");

        assertTrue(greenMail.waitForIncomingEmail(10_000, 1));
        Message[] received = greenMail.getReceivedMessagesForDomain("example.invalid");
        Message delivered = java.util.Arrays.stream(received)
                .filter(message -> subjectOf(message).equals("Your license"))
                .findFirst()
                .orElseThrow();
        assertEquals("Your license", subjectOf(delivered));
        assertTrue(delivered.getContent().toString().contains("Here is your key"));
    }

    @Test
    @DisplayName("The poll hands every unread mail to the listeners")
    void checkNotifiesListeners() throws Exception {
        List<String> seen = new ArrayList<>();
        service.registerMessageListener(message -> seen.add(subjectOf(message)));
        deliverToInbox("First purchase");
        deliverToInbox("Second purchase");

        check();

        assertEquals(List.of("First purchase", "Second purchase"), seen);
    }

    @Test
    @DisplayName("A mail already handled is not handed over a second time")
    void checkSkipsMailItAlreadyRead() throws Exception {
        List<String> seen = new ArrayList<>();
        service.registerMessageListener(message -> seen.add(subjectOf(message)));
        deliverToInbox("Only once");

        check();
        check();

        assertEquals(List.of("Only once"), seen);
    }

    @Test
    @DisplayName("A listener that fails does not stop the mail after it from being handled")
    void oneFailingListenerDoesNotStopTheRound() throws Exception {
        List<String> seen = new ArrayList<>();
        service.registerMessageListener(message -> {
            throw new IllegalStateException("the handler could not make sense of it");
        });
        service.registerMessageListener(message -> seen.add(subjectOf(message)));
        deliverToInbox("Still handled");

        check();

        assertEquals(List.of("Still handled"), seen);
    }

    @Test
    @DisplayName("An empty inbox is a round that does nothing")
    void checkOnEmptyInboxIsQuiet() throws Exception {
        List<String> seen = new ArrayList<>();
        service.registerMessageListener(message -> seen.add(subjectOf(message)));

        check();

        assertTrue(seen.isEmpty());
    }

    private void deliverToInbox(String subject) {
        com.icegreen.greenmail.util.GreenMailUtil.sendTextEmail(
                USER, "service@paypal.de", subject, "body", greenMail.getSmtp().getServerSetup());
        assertTrue(greenMail.waitForIncomingEmail(10_000, 1));
    }

    private static String subjectOf(Message message) {
        try {
            return message.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
