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
    private Map<String, String> properties = Collections.emptyMap();

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
