package de.chojo.lyna.core;

import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.web.WebService;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.mail.MailingService;

public class Web {
    private final Configuration<ConfigFile> configuration;
    private final Data data;
    private final MailingService mailingService;
    private WebService webService;

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
        webService = WebService.create(configuration, data, mailingService);
    }

    public WebService webService() {
        return webService;
    }
}
