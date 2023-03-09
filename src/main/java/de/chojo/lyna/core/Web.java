package de.chojo.lyna.core;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.api.Api;
import de.chojo.lyna.configuration.ConfigFile;

public class Web {
    private final Configuration<ConfigFile> configuration;
    private final Data data;
    private Api api;

    public Web(Configuration<ConfigFile> configuration, Data data) {
        this.configuration = configuration;
        this.data = data;
    }

    public static Web create(Configuration<ConfigFile> configuration, Data data) {
        Web web = new Web(configuration, data);
        web.init();
        return web;
    }

    private void init() {
        api = Api.create(configuration, data.nexus());
    }

    public Api api() {
        return api;
    }
}
