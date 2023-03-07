package de.chojo.lyna.configuration.elements;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class Nexus {
    private String host = "eldonexus.de";
    private String username = "admin";
    private String password = "passy";

    public String host() {
        return host;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }
}
