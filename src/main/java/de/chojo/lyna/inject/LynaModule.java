package de.chojo.lyna.inject;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.auth.DiscordOAuthClient;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.configuration.elements.Auth;
import de.chojo.lyna.configuration.elements.BaseSettings;
import de.chojo.lyna.configuration.elements.Database;
import de.chojo.lyna.configuration.elements.Demo;
import de.chojo.lyna.configuration.elements.Discord;
import de.chojo.lyna.configuration.elements.Kofi;
import de.chojo.lyna.configuration.elements.License;
import de.chojo.lyna.configuration.elements.Links;
import de.chojo.lyna.configuration.elements.Mailing;
import de.chojo.lyna.configuration.elements.Nexus;
import de.chojo.lyna.configuration.elements.discord.OAuth;
import de.chojo.lyna.data.access.AccountLicenses;
import de.chojo.lyna.data.access.AccountSessions;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.access.DemoArtifacts;
import de.chojo.lyna.data.access.DownloadLog;
import de.chojo.lyna.data.access.EmailVerificationTokens;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.access.InstanceOperators;
import de.chojo.lyna.data.access.InstanceSettingsAccess;
import de.chojo.lyna.data.access.KioskProducts;
import de.chojo.lyna.data.access.KoFiProducts;
import de.chojo.lyna.data.access.LicenseInvites;
import de.chojo.lyna.data.access.Mailings;
import de.chojo.lyna.data.access.PasswordResetTokens;
import de.chojo.lyna.data.access.Products;
import de.chojo.lyna.data.access.RevokedJtis;
import de.chojo.lyna.core.Threading;
import de.chojo.nexus.NexusRest;

/**
 * Everything the application is built from, and how each part reaches the others.
 *
 * <p>Startup used to be a fixed order of {@code create(...)} calls ending in a back-patch, because
 * {@code Data} was built before {@code Bot} and needed something from it. Declaring the parts and
 * letting them ask for what they need removes both the order and the back-patch.
 *
 * <p>Configuration elements are bound one by one rather than only as a whole, so a class asks for the
 * {@link de.chojo.lyna.configuration.elements.Database} settings instead of reaching through the file
 * for them. What a class needs is then visible in its constructor.
 */
public class LynaModule extends AbstractModule {
    private final Conf conf;

    public LynaModule(Conf conf) {
        this.conf = conf;
    }

    @Provides
    @Singleton
    Conf conf() {
        return conf;
    }

    /**
     * <p>Read once. Ocular has already applied whatever the environment supplied, and nothing saves
     * it again - saving would write those values into the file.
     */
    @Provides
    @Singleton
    ConfigFile config(Conf conf) {
        return conf.main();
    }

    @Provides @Singleton BaseSettings baseSettings(ConfigFile config) { return config.baseSettings(); }

    @Provides @Singleton Database database(ConfigFile config) { return config.database(); }

    @Provides @Singleton Links links(ConfigFile config) { return config.links(); }

    @Provides @Singleton License license(ConfigFile config) { return config.license(); }

    @Provides @Singleton Nexus nexus(ConfigFile config) { return config.nexus(); }

    @Provides @Singleton Api api(ConfigFile config) { return config.api(); }

    @Provides @Singleton Mailing mailing(ConfigFile config) { return config.mailing(); }

    @Provides @Singleton Kofi kofi(ConfigFile config) { return config.kofi(); }

    @Provides @Singleton Auth auth(ConfigFile config) { return config.auth(); }

    @Provides @Singleton Discord discord(ConfigFile config) { return config.discord(); }

    @Provides @Singleton OAuth oauth(Discord discord) { return discord.oauth(); }

    @Provides @Singleton Demo demo(ConfigFile config) { return config.demo(); }

    /**
     * The data-access objects.
     *
     * <p>They are held one apiece rather than made where they are needed. Most carry no state, but
     * {@link Guilds} caches and holds the reference to whatever grants roles, so a second instance
     * would be a second cache and a gateway nobody had told about.
     *
     * <p>They speak to the database through sadu's global query configuration rather than a
     * {@code DataSource} of their own, which is why building one is safe before the pool exists.
     * Using one is not, and {@link de.chojo.lyna.core.Data#start()} is what makes it so.
     *
     * <p>{@link Threading} is held once for a harder reason: it owns the thread pools, and a second
     * one would quietly be a second set of them.
     */
    @Override
    protected void configure() {
        bind(Threading.class).in(Singleton.class);

        bind(Accounts.class).in(Singleton.class);
        bind(AccountLicenses.class).in(Singleton.class);
        bind(LicenseInvites.class).in(Singleton.class);
        bind(AccountSessions.class).in(Singleton.class);
        bind(RevokedJtis.class).in(Singleton.class);
        bind(DownloadLog.class).in(Singleton.class);
        bind(DemoArtifacts.class).in(Singleton.class);
        bind(InstanceSettingsAccess.class).in(Singleton.class);
        bind(InstanceOperators.class).in(Singleton.class);
        bind(PasswordResetTokens.class).in(Singleton.class);
        bind(EmailVerificationTokens.class).in(Singleton.class);
        bind(KioskProducts.class).in(Singleton.class);
        bind(PasswordHasher.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    NexusRest nexusRest(Nexus nexus) {
        return NexusRest.builder(nexus.host())
                .setPasswordAuth(nexus.username(), nexus.password())
                .build();
    }

    @Provides @Singleton Guilds guilds(NexusRest nexus, Conf conf) { return new Guilds(nexus, conf); }

    @Provides @Singleton Products products(Guilds guilds) { return new Products(guilds); }

    @Provides @Singleton Mailings mailings(Guilds guilds) { return new Mailings(guilds); }

    @Provides @Singleton KoFiProducts koFiProducts(Products products) { return new KoFiProducts(products); }

    @Provides @Singleton JwtService jwtService(Auth auth) { return new JwtService(auth); }

    @Provides @Singleton DiscordOAuthClient discordOAuthClient(OAuth oauth) { return new DiscordOAuthClient(oauth); }
}
