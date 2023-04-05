package de.chojo.lyna.configuration.elements;

public class Mailing {
    String host;
    String user;
    String password;
    String directory;
    int frequency;

    public String host() {
        return host;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public String directory() {
        return directory;
    }

    public int frequency() {
        return frequency;
    }
}
