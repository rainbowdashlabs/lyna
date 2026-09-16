package de.chojo.lyna.core;

import com.google.inject.Inject;
import de.chojo.lyna.web.WebService;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.demo.DemoService;
import de.chojo.lyna.gateway.Gateway;
import de.chojo.lyna.mail.MailingService;

public class Web {
    private final Gateway gateway;
    private final Conf configuration;
    private final Data data;
    private final MailingService mailingService;
    private final DemoService demoService;
    private WebService webService;

    @Inject
    public Web(Conf configuration, Data data, MailingService mailingService,
               DemoService demoService, Gateway gateway) {
        this.gateway = gateway;
        this.configuration = configuration;
        this.data = data;
        this.mailingService = mailingService;
        this.demoService = demoService;
    }

    /**
     * Binds the port and mounts the API. Separate from construction because a constructor an injector
     * calls has no business listening on a socket.
     */
    public void start() {
        webService = WebService.create(gateway, configuration, data, mailingService, demoService);
    }

    public WebService webService() {
        return webService;
    }
}
