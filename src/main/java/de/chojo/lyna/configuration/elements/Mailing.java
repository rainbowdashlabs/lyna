package de.chojo.lyna.configuration.elements;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class Mailing {
    String host;
    String user;
    String password;

    public String host() {
        return host;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }
}
