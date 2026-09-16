package de.chojo.lyna.web.api;

import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.web.WebService;
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
    private final WebService web;
    private final Conf configuration;
    private final NexusRest nexus;
    private final V1 v1;
    private final Auth auth;
    private final Account account;
    private final Theme theme;
    private final Admin admin;

    private static final Logger log = getLogger(Api.class);

    public Api(WebService web, Conf configuration, Data data, MailingService mailingService,
               de.chojo.lyna.demo.DemoService demoService) {
        this.web = web;
        this.configuration = configuration;
        this.nexus = data.nexus();
        auth = new Auth(configuration, data.accounts(), data.accountSessions(), data.revokedJtis(),
                data.passwordResetTokens(), data.emailVerificationTokens(), data.passwordHasher(), data.jwtService(),
                data.discordOAuthClient(), mailingService);
        v1 = new V1(this, data.products(), mailingService, data.kofi(), data.downloadLog(),
                data.kioskProducts(), auth, data.accounts(), data.accountLicenses(),
                data.accountSessions(), data.jwtService(), demoService);
        account = new Account(auth, data.accounts(), data.accountLicenses(), data.licenseInvites(), data.instanceSettings(), mailingService, data.emailVerificationTokens(), configuration,
                data.accountSessions(), data.revokedJtis(),
                data.downloadLog(), data.passwordHasher(), data.jwtService());
        theme = new Theme(data.instanceSettings());
        admin = new Admin(auth, configuration, data.accounts(), data.guilds(), data.instanceSettings(), data.kofi(), data.kioskProducts(),
                data.instanceOperators(), mailingService);
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

    public void gateway(Gateway gateway) {
        admin.gateway(gateway);
    }

    public Conf configuration() {
        return configuration;
    }

    public NexusRest nexus() {
        return nexus;
    }

    public V1 v1() {
        return v1;
    }

    public Auth auth() {
        return auth;
    }

    public Account account() {
        return account;
    }
}
