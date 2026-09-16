package de.chojo.lyna.web.api;

import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.mail.MailingService;
import com.google.inject.Inject;
import de.chojo.lyna.web.api.account.Account;
import de.chojo.lyna.web.api.admin.Admin;
import de.chojo.lyna.web.api.auth.Auth;
import de.chojo.lyna.web.api.theme.Theme;
import de.chojo.lyna.web.api.v1.V1;
import de.chojo.nexus.NexusRest;
import de.chojo.lyna.gateway.Gateway;
import org.slf4j.Logger;

import static io.javalin.apibuilder.ApiBuilder.path;
import static org.slf4j.LoggerFactory.getLogger;

public class Api {
    private final Conf configuration;
    private final NexusRest nexus;
    private final V1 v1;
    private final Auth auth;
    private final Account account;
    private final Theme theme;
    private final Admin admin;

    private static final Logger log = getLogger(Api.class);

    @Inject
    public Api(Conf configuration, NexusRest nexus, V1 v1, Auth auth, Account account, Theme theme,
               Admin admin) {
        this.configuration = configuration;
        this.nexus = nexus;
        this.v1 = v1;
        this.auth = auth;
        this.account = account;
        this.theme = theme;
        this.admin = admin;
    }

    public void init() {
        path("api", () -> {
            v1.init();
            auth.init();
            account.init();
            theme.init();
            admin.init();
        });
    }





    public Auth auth() {
        return auth;
    }

    public Account account() {
        return account;
    }
}
