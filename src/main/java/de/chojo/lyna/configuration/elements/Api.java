package de.chojo.lyna.configuration.elements;

import java.util.List;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class Api {
    private String hostname;
    private String url;
    private String host;
    private int port;
    private List<String> allowedOrigins = List.of();
    private List<String> iconHosts = List.of();
    private boolean staticUi = true;

    public String hostname() {
        return hostname;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String url() {
        return url;
    }

    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    public List<String> iconHosts() {
        return iconHosts;
    }

    public boolean staticUi() {
        return staticUi;
    }
}
