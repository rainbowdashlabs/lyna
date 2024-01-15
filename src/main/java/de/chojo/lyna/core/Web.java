package de.chojo.lyna.core;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.api.Api;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.mail.MailingService;

public class Web {
    private final Configuration<ConfigFile> configuration;
    private final Data data;
    private final MailingService mailingService;
    private Api api;

    public Web(Configuration<ConfigFile> configuration, Data data, MailingService mailingService) {
        this.configuration = configuration;
        this.data = data;
        this.mailingService = mailingService;
    }

    public static Web create(Configuration<ConfigFile> configuration, Data data, MailingService mailingService) {
        Web web = new Web(configuration, data, mailingService);
        web.init();
        return web;
    }

    private void init() {
        api = Api.create(configuration, data, mailingService);
    }

    public Api api() {
        return api;
    }
}
