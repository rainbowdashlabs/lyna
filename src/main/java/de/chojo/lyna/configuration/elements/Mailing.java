package de.chojo.lyna.configuration.elements;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class Mailing {
    private MailSettings smtp = new MailSettings();
    private MailSettings imap = new MailSettings();
    private String user = "";
    private String password = "";
    private List<String> originMail = java.util.List.of("");
    private int pollSeconds = 300;
    // While we do no use javamail, we use angus and both implement jakarta.mail
    // Most of the parameters of javamail can be applied here as well
    // https://www.tutorialspoint.com/javamail_api/javamail_api_imap_servers.htm
    // https://www.tutorialspoint.com/javamail_api/javamail_api_smtp_servers.htm
    private Map<String, String> properties = Collections.emptyMap();

    public int pollSeconds() {
        return pollSeconds;
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

    public Map<String, String> properties() {
        return properties;
    }
}
