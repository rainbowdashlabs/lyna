package de.chojo.lyna.web.api;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.web.WebService;
import de.chojo.lyna.web.api.v1.V1;
import de.chojo.nexus.NexusRest;
import org.slf4j.Logger;

import static io.javalin.apibuilder.ApiBuilder.path;
import static org.slf4j.LoggerFactory.getLogger;

public class Api {
    private final WebService web;
    private final Configuration<ConfigFile> configuration;
    private final NexusRest nexus;
    private final V1 v1;

    private static final Logger log = getLogger(Api.class);

    public Api(WebService web, Configuration<ConfigFile> configuration, Data data, MailingService mailingService) {
        this.web = web;
        this.configuration = configuration;
        this.nexus = data.nexus();
        v1 = new V1(this, data.products(), mailingService, data.kofi());
    }

    public void init() {
        path("api", v1::init);
    }

    public Configuration<ConfigFile> configuration() {
        return configuration;
    }

    public NexusRest nexus() {
        return nexus;
    }

    public V1 v1() {
        return v1;
    }
}
