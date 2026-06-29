package de.chojo.lyna.configuration.elements;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class Auth {
    private String jwtSecret = "";
    private long jwtExpirySeconds = 86400L;

    public String jwtSecret() {
        return jwtSecret;
    }

    public long jwtExpirySeconds() {
        return jwtExpirySeconds;
    }
}
