package de.chojo.lyna.configuration.elements;

public class MailSettings {
    private String host = "";
    private int port = 665;
    private boolean ssl = false;

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean ssl() {
        return ssl;
    }
}
