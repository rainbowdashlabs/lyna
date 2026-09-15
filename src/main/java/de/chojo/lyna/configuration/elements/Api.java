package de.chojo.lyna.configuration.elements;

import de.chojo.jdautil.util.SysVar;

import java.util.List;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class Api {
    private String hostname;
    private String url;
    private String host;
    private int port;
    private List<String> allowedOrigins = List.of();
    private List<String> iconHosts = List.of();
    private boolean staticUi = false;

    public String hostname() {
        return hostname;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    /**
     * The address this API is reached at from outside, which is what the one-time download links
     * are built from.
     *
     * <p>`LYNA_API_URL` overrides the configured value. A deployment whose published port is not
     * fixed - the end-to-end stack derives its own per checkout - would otherwise mint links
     * pointing at a port nothing answers on.
     *
     * @return the public base address, without a trailing slash
     */
    public String url() {
        String override = SysVar.envOrProp("LYNA_API_URL", "lyna.api.url", null);
        return override == null || override.isBlank() ? url : override;
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
