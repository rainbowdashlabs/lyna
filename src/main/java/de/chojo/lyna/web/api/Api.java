package de.chojo.lyna.web.api;

import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.web.WebService;
import de.chojo.lyna.web.api.account.Account;
import de.chojo.lyna.web.api.admin.Admin;
import de.chojo.lyna.web.api.auth.Auth;
import de.chojo.lyna.web.api.v1.V1;
import net.dv8tion.jda.api.sharding.ShardManager;
import de.chojo.nexus.NexusRest;
import org.slf4j.Logger;

import static io.javalin.apibuilder.ApiBuilder.path;
import static org.slf4j.LoggerFactory.getLogger;

public class Api {
    private final WebService web;
    private final Configuration<ConfigFile> configuration;
    private final NexusRest nexus;
    private final V1 v1;
    private final Auth auth;
    private final Account account;
    private final Admin admin;

    private static final Logger log = getLogger(Api.class);

    public Api(WebService web, Configuration<ConfigFile> configuration, Data data, MailingService mailingService) {
        this.web = web;
        this.configuration = configuration;
        this.nexus = data.nexus();
        v1 = new V1(this, data.products(), mailingService, data.kofi());
        auth = new Auth(configuration, data.accounts(), data.accountSessions(), data.revokedJtis(),
                data.passwordHasher(), data.jwtService(), data.discordOAuthClient());
        account = new Account(auth, data.accounts(), data.accountSessions(), data.revokedJtis(),
                data.downloadLog(), data.passwordHasher(), data.jwtService());
        admin = new Admin(auth, configuration, data.accounts(), data.guilds(), data.instanceSettings());
    }

    public void init() {
        path("api", () -> {
            v1.init();
            auth.init();
            account.init();
            admin.init();
        });
    }

    public void shardManager(ShardManager shardManager) {
        admin.shardManager(shardManager);
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

    public Auth auth() {
        return auth;
    }

    public Account account() {
        return account;
    }
}
