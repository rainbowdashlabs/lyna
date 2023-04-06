package de.chojo.lyna.configuration.elements;

import java.util.List;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class Mailing {
    private String host = "";
    private String user = "";
    private String password = "";
    private List<String> originMail = java.util.List.of("");

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
}
