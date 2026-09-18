/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.inject;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.auth.DiscordOAuthClient;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.commands.info.Info;
import de.chojo.lyna.commands.kofi.KoFi;
import de.chojo.lyna.commands.register.Register;
import de.chojo.lyna.commands.registrations.Registrations;
import de.chojo.lyna.commands.settings.Settings;
import de.chojo.lyna.commands.trial.Trial;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.configuration.elements.Auth;
import de.chojo.lyna.configuration.elements.BaseSettings;
import de.chojo.lyna.configuration.elements.Database;
import de.chojo.lyna.configuration.elements.Demo;
import de.chojo.lyna.configuration.elements.Discord;
import de.chojo.lyna.configuration.elements.Downloads;
import de.chojo.lyna.configuration.elements.Kofi;
import de.chojo.lyna.configuration.elements.License;
import de.chojo.lyna.configuration.elements.Links;
import de.chojo.lyna.configuration.elements.Mailing;
import de.chojo.lyna.configuration.elements.Nexus;
import de.chojo.lyna.configuration.elements.discord.OAuth;
import de.chojo.lyna.core.Bot;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.core.Threading;
import de.chojo.lyna.data.access.DemoArtifacts;
import de.chojo.lyna.data.access.DownloadLog;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.access.InstanceOperators;
import de.chojo.lyna.data.access.InstanceSettingsAccess;
import de.chojo.lyna.data.access.KioskProducts;
import de.chojo.lyna.data.access.KoFiProducts;
import de.chojo.lyna.data.access.LicenseInvites;
import de.chojo.lyna.data.access.Mailings;
import de.chojo.lyna.data.access.Products;
import de.chojo.lyna.data.roles.JdaRoleSync;
import de.chojo.lyna.data.roles.RoleSync;
import de.chojo.lyna.demo.DemoService;
import de.chojo.lyna.feature.account.repository.AccountEmailRepository;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.account.repository.AccountSessionRepository;
import de.chojo.lyna.feature.account.repository.EmailVerificationTokenRepository;
import de.chojo.lyna.feature.account.repository.PasswordResetTokenRepository;
import de.chojo.lyna.feature.account.repository.RevokedJtiRepository;
import de.chojo.lyna.feature.account.service.AccountEmailService;
import de.chojo.lyna.feature.account.service.AccountLinkService;
import de.chojo.lyna.feature.account.service.AccountService;
import de.chojo.lyna.feature.account.service.PurchaseCollectionService;
import de.chojo.lyna.feature.account.service.UsernameService;
import de.chojo.lyna.feature.license.repository.LicenseRepository;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.license.service.LicenseSharingService;
import de.chojo.lyna.feature.product.repository.ProductRepository;
import de.chojo.lyna.feature.product.service.ProductRoleService;
import de.chojo.lyna.feature.product.service.TrialService;
import de.chojo.lyna.gateway.Gateway;
import de.chojo.lyna.gateway.JdaGateway;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.web.WebService;
import de.chojo.lyna.web.api.account.Account;
import de.chojo.lyna.web.api.admin.Admin;
import de.chojo.lyna.web.api.theme.Theme;
import de.chojo.lyna.web.api.v1.V1;
import de.chojo.lyna.web.api.v1.demo.DemoApi;
import de.chojo.lyna.web.api.v1.download.Download;
import de.chojo.lyna.web.api.v1.download.direct.Direct;
import de.chojo.lyna.web.api.v1.download.proxy.Proxy;
import de.chojo.lyna.web.api.v1.kofi.KoFiApi;
import de.chojo.lyna.web.api.v1.products.Wizard;
import de.chojo.lyna.web.api.v1.releases.Releases;
import de.chojo.lyna.web.api.v1.update.Update;
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

    @Provides
    @Singleton
    BaseSettings baseSettings(ConfigFile config) {
        return config.baseSettings();
    }

    @Provides
    @Singleton
    Database database(ConfigFile config) {
        return config.database();
    }

    @Provides
    @Singleton
    Links links(ConfigFile config) {
        return config.links();
    }

    @Provides
    @Singleton
    License license(ConfigFile config) {
        return config.license();
    }

    @Provides
    @Singleton
    Nexus nexus(ConfigFile config) {
        return config.nexus();
    }

    @Provides
    @Singleton
    Api api(ConfigFile config) {
        return config.api();
    }

    @Provides
    @Singleton
    Mailing mailing(ConfigFile config) {
        return config.mailing();
    }

    @Provides
    @Singleton
    Kofi kofi(ConfigFile config) {
        return config.kofi();
    }

    @Provides
    @Singleton
    Auth auth(ConfigFile config) {
        return config.auth();
    }

    @Provides
    @Singleton
    Discord discord(ConfigFile config) {
        return config.discord();
    }

    @Provides
    @Singleton
    OAuth oauth(Discord discord) {
        return discord.oauth();
    }

    @Provides
    @Singleton
    Demo demo(ConfigFile config) {
        return config.demo();
    }

    @Provides
    @Singleton
    Downloads downloads(ConfigFile config) {
        return config.downloads();
    }

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
        Multibinder<SlashProvider<Slash>> commands =
                Multibinder.newSetBinder(binder(), new TypeLiteral<SlashProvider<Slash>>() {});
        commands.addBinding().to(de.chojo.lyna.commands.products.Products.class);
        commands.addBinding().to(de.chojo.lyna.commands.license.License.class);
        commands.addBinding().to(Register.class);
        commands.addBinding().to(Registrations.class);
        commands.addBinding().to(Settings.class);
        commands.addBinding().to(Info.class);
        commands.addBinding().to(de.chojo.lyna.commands.downloads.Downloads.class);
        commands.addBinding().to(de.chojo.lyna.commands.download.Download.class);
        commands.addBinding().to(Trial.class);
        commands.addBinding().to(de.chojo.lyna.commands.mailing.Mailing.class);
        commands.addBinding().to(KoFi.class);

        bind(Threading.class).in(Singleton.class);
        bind(Data.class).in(Singleton.class);
        bind(MailingService.class).in(Singleton.class);
        bind(DemoService.class).in(Singleton.class);
        bind(WebService.class).in(Singleton.class);
        bind(de.chojo.lyna.web.api.Api.class).in(Singleton.class);
        bind(V1.class).in(Singleton.class);
        bind(de.chojo.lyna.web.api.auth.Auth.class).in(Singleton.class);
        bind(Account.class).in(Singleton.class);
        bind(Theme.class).in(Singleton.class);
        bind(Admin.class).in(Singleton.class);
        bind(Download.class).in(Singleton.class);
        bind(Direct.class).in(Singleton.class);
        bind(Update.class).in(Singleton.class);
        bind(KoFiApi.class).in(Singleton.class);
        bind(Releases.class).in(Singleton.class);
        bind(Wizard.class).in(Singleton.class);
        bind(DemoApi.class).in(Singleton.class);

        // The token cache lives here: a link minted by one instance would not be redeemable by
        // another, and the bot mints links through the same object the API serves them from.
        bind(Proxy.class).in(Singleton.class);
        bind(Bot.class).in(Singleton.class);

        bind(AccountRepository.class).in(Singleton.class);
        bind(AccountService.class).in(Singleton.class);
        bind(AccountEmailService.class).in(Singleton.class);
        bind(AccountLinkService.class).in(Singleton.class);
        bind(UsernameService.class).in(Singleton.class);
        bind(PurchaseCollectionService.class).in(Singleton.class);
        bind(LicenseRepository.class).in(Singleton.class);
        bind(LicenseSharingService.class).in(Singleton.class);
        bind(LicenseService.class).in(Singleton.class);
        bind(ProductRepository.class).in(Singleton.class);
        bind(ProductRoleService.class).in(Singleton.class);
        bind(TrialService.class).in(Singleton.class);
        bind(AccountEmailRepository.class).in(Singleton.class);
        bind(AccountLicenseRepository.class).in(Singleton.class);
        bind(LicenseInvites.class).in(Singleton.class);
        bind(AccountSessionRepository.class).in(Singleton.class);
        bind(RevokedJtiRepository.class).in(Singleton.class);
        bind(DownloadLog.class).in(Singleton.class);
        bind(DemoArtifacts.class).in(Singleton.class);
        bind(InstanceSettingsAccess.class).in(Singleton.class);
        bind(InstanceOperators.class).in(Singleton.class);
        bind(PasswordResetTokenRepository.class).in(Singleton.class);
        bind(EmailVerificationTokenRepository.class).in(Singleton.class);
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

    /**
     * The gateway, which only answers once the bot has connected.
     *
     * <p>Takes a {@link Provider} rather than the bot itself, and that is the whole trick: the bot
     * needs the commands, the commands need the data access, and the data access needs a gateway to
     * take roles back through. Asking for the bot lazily makes that a sequence rather than a circle -
     * nothing calls {@code get()} until somebody actually asks Discord a question, by which time the
     * bot is there.
     */
    @Provides
    @Singleton
    Gateway gateway(BaseSettings settings, Provider<Bot> bot) {
        if (!settings.botEnabled()) return Gateway.NONE;
        return new JdaGateway(() -> bot.get().shardManager());
    }

    @Provides
    @Singleton
    RoleSync roleSync(BaseSettings settings, Gateway gateway, ProductRoleService productRoles) {
        return settings.botEnabled() ? new JdaRoleSync(gateway, productRoles) : RoleSync.NOOP;
    }

    /**
     * <p>Given what keeps its roles in step here rather than being told afterwards, which is what
     * {@code Data#inject} used to do once the bot had connected.
     */
    @Provides
    @Singleton
    Guilds guilds(NexusRest nexus, Conf conf, RoleSync roleSync, AccountLinkService accountLinks) {
        Guilds guilds = new Guilds(nexus, conf, accountLinks);
        guilds.roles(roleSync);
        return guilds;
    }

    @Provides
    @Singleton
    Products products(Guilds guilds) {
        return new Products(guilds);
    }

    @Provides
    @Singleton
    Mailings mailings(Guilds guilds) {
        return new Mailings(guilds);
    }

    @Provides
    @Singleton
    KoFiProducts koFiProducts(Products products) {
        return new KoFiProducts(products);
    }

    /**
     * <p>Built by its own factory, which reads the version off the classpath - something a
     * constructor cannot do without being handed the answer.
     */
    @Provides
    @Singleton
    Info info(Conf conf) {
        return Info.create(conf);
    }

    @Provides
    @Singleton
    JwtService jwtService(Auth auth) {
        return new JwtService(auth);
    }

    @Provides
    @Singleton
    DiscordOAuthClient discordOAuthClient(OAuth oauth) {
        return new DiscordOAuthClient(oauth);
    }
}
