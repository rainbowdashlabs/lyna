package de.chojo.lyna.core;

import de.chojo.lyna.web.WebService;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.demo.DemoService;
import de.chojo.lyna.mail.MailingService;

public class Web {
    private final Conf configuration;
    private final Data data;
    private final MailingService mailingService;
    private final DemoService demoService;
    private WebService webService;

    public Web(Conf configuration, Data data, MailingService mailingService,
               DemoService demoService) {
        this.configuration = configuration;
        this.data = data;
        this.mailingService = mailingService;
        this.demoService = demoService;
    }

    public static Web create(Conf configuration, Data data, MailingService mailingService,
                             DemoService demoService) {
        Web web = new Web(configuration, data, mailingService, demoService);
        web.init();
        return web;
    }

    private void init() {
        webService = WebService.create(configuration, data, mailingService, demoService);
    }

    public WebService webService() {
        return webService;
    }
}
