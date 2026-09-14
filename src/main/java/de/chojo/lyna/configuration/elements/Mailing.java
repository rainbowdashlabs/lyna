package de.chojo.lyna.configuration.elements;

import de.chojo.lyna.configuration.elements.mailing.MailSettings;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class Mailing {
    private MailSettings smtp = new MailSettings();
    private MailSettings imap = new MailSettings();
    private String user = "";
    private String password = "";
    private List<String> originMail = java.util.List.of("");
    private int pollSeconds = 300;
    private boolean enabled = true;
    // While we do no use javamail, we use angus and both implement jakarta.mail
    // Most of the parameters of javamail can be applied here as well
    // https://www.tutorialspoint.com/javamail_api/javamail_api_imap_servers.htm
    // https://www.tutorialspoint.com/javamail_api/javamail_api_smtp_servers.htm
    private Map<String, String> properties = Collections.emptyMap();

    public int pollSeconds() {
        return pollSeconds;
    }

    /**
     * Whether mail is polled and sent at all.
     *
     * <p>Off, nothing connects to the mail server and nothing is sent. A deployment that grants its
     * licenses through Ko-fi or the bot needs no mailbox, and the end-to-end stack has none.
     *
     * @return whether to reach the mail server
     */
    public boolean enabled() {
        return enabled;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public List<String> originMails() {
        return originMail;
    }

    public MailSettings smtp() {
        return smtp;
    }

    public MailSettings imap() {
        return imap;
    }

    public Properties properties() {
        Properties props = new Properties();
        props.putAll(smtp().properties("smtp"));
        props.putAll(imap().properties("imap"));
        props.putAll(properties);
        return props;
    }
}
