package de.chojo.lyna.configuration.elements;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class Mailing {
    private String host = "";
    private String user = "";
    private String password = "";
    private List<String> originMail = java.util.List.of("");
    private boolean sslSmtp = false;
    private boolean sslImap = false;
    private int pollSeconds = 300;
    // While we do no use javamail, we use angus and both implement jakarta.mail
    // Most of the parameters of javamail can be applied here as well
    // https://www.tutorialspoint.com/javamail_api/javamail_api_imap_servers.htm
    // https://www.tutorialspoint.com/javamail_api/javamail_api_smtp_servers.htm
    private Map<String, String> properties = Collections.emptyMap();

    public int pollSeconds() {
        return pollSeconds;
    }

    public String host() {
        return host;
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

    public boolean sslSmtp() {
        return sslSmtp;
    }

    public boolean sslImap() {
        return sslImap;
    }

    public Map<String, String> properties() {
        return properties;
    }
}
