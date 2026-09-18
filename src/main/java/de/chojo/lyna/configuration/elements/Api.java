package de.chojo.lyna.configuration.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.Prop;
import dev.chojo.ocular.override.OverwritePrefix;


import java.util.List;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("API")
public class Api {
    @Overwrite(env = @Env, prop = @Prop)
    private String hostname;
    @Overwrite(env = @Env, prop = @Prop)
    private String url;
    @Overwrite(env = @Env, prop = @Prop)
    private String host;
    @Overwrite(env = @Env, prop = @Prop)
    private int port;
    @Overwrite(env = @Env, prop = @Prop)
    private List<String> allowedOrigins = List.of();
    @Overwrite(env = @Env, prop = @Prop)
    private List<String> iconHosts = List.of();
    @Overwrite(env = @Env, prop = @Prop)
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
     * <p>{@code API_URL} overrides it, which a deployment whose published port is not fixed needs -
     * the end-to-end stack derives its own per checkout, and would otherwise mint links pointing at
     * a port nothing answers on.
     *
     * @return the public base address, without a trailing slash
     */
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
