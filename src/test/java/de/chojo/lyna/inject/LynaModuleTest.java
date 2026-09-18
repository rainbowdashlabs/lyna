/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.inject;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.configuration.TestConf;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.configuration.elements.Auth;
import de.chojo.lyna.configuration.elements.Database;
import de.chojo.lyna.configuration.elements.Demo;
import de.chojo.lyna.configuration.elements.Mailing;
import de.chojo.lyna.configuration.elements.discord.OAuth;
import de.chojo.lyna.core.Bot;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.core.Threading;
import de.chojo.lyna.data.roles.RoleSync;
import de.chojo.lyna.demo.DemoSchedule;
import de.chojo.lyna.demo.DemoService;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.account.repository.AccountSessionRepository;
import de.chojo.lyna.feature.account.repository.EmailVerificationTokenRepository;
import de.chojo.lyna.feature.account.repository.PasswordResetTokenRepository;
import de.chojo.lyna.feature.account.repository.RevokedJtiRepository;
import de.chojo.lyna.feature.demo.repository.DemoArtifactRepository;
import de.chojo.lyna.feature.download.repository.DownloadLogRepository;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.instance.repository.InstanceOperatorRepository;
import de.chojo.lyna.feature.instance.repository.InstanceSettingsRepository;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.license.repository.LicenseInviteRepository;
import de.chojo.lyna.feature.mail.repository.MailingLookup;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.feature.purchase.repository.KoFiProductRepository;
import de.chojo.lyna.gateway.Gateway;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * That the application can still be built from its parts.
 *
 * <p>The rest of the suite covers repositories and services. Neither notices a binding that is
 * missing, wrong, or circular - Guice finds that out when something asks, which without this test
 * means at startup, in a deployment. Asking here for the top of each group turns that into a failing
 * build.
 */
class LynaModuleTest {

    /**
     * An instance serving the HTTP API and nothing else, which is what the end-to-end stack is.
     *
     * <p>{@code botEnabled} is stated rather than left out: it defaults to on, so a configuration
     * that says nothing is a configuration with a bot.
     *
     * <p>The signing secret is stated for a different reason - {@code JwtService} refuses to exist
     * without one, which is right of it and means a configuration of pure defaults cannot build the
     * graph at all.
     */
    private static Conf configured() {
        return TestConf.from("""
                auth:
                  jwtSecret: "a-secret-long-enough-to-sign-with-000000"
                baseSettings:
                  botEnabled: false
                """);
    }

    private Injector injector() {
        return Guice.createInjector(new LynaModule(configured()));
    }

    @Test
    @DisplayName("The module can be built at all")
    void moduleIsCreatable() {
        assertNotNull(injector());
    }

    @Test
    @DisplayName("The configuration is reachable, and is the one the module was given")
    void configurationIsBound() {
        Conf conf = configured();
        Injector injector = Guice.createInjector(new LynaModule(conf));

        assertSame(conf, injector.getInstance(Conf.class));
        assertSame(conf.main(), injector.getInstance(ConfigFile.class));
    }

    @Test
    @DisplayName("Asking twice for something held once gives the same instance")
    void singletonsAreSingletons() {
        Injector injector = injector();

        assertSame(injector.getInstance(ConfigFile.class), injector.getInstance(ConfigFile.class));
    }

    @Test
    @DisplayName("Every data-access object can be built, and is held once")
    void daosAreBuiltOnce() {
        Injector injector = injector();

        for (Class<?> dao : List.of(
                AccountRepository.class,
                AccountLicenseRepository.class,
                LicenseInviteRepository.class,
                AccountSessionRepository.class,
                RevokedJtiRepository.class,
                DownloadLogRepository.class,
                DemoArtifactRepository.class,
                InstanceSettingsRepository.class,
                InstanceOperatorRepository.class,
                PasswordResetTokenRepository.class,
                EmailVerificationTokenRepository.class,
                KioskProductRepository.class,
                Guilds.class,
                ProductLookup.class,
                MailingLookup.class,
                KoFiProductRepository.class)) {
            assertNotNull(injector.getInstance(dao), dao.getSimpleName() + " could not be built");
            assertSame(
                    injector.getInstance(dao), injector.getInstance(dao), dao.getSimpleName() + " should be held once");
        }
    }

    @Test
    @DisplayName("Every configuration element is reachable on its own")
    void configElementsAreBound() {
        Injector injector = injector();
        ConfigFile config = injector.getInstance(ConfigFile.class);

        assertSame(config.database(), injector.getInstance(Database.class));
        assertSame(config.api(), injector.getInstance(Api.class));
        assertSame(config.auth(), injector.getInstance(Auth.class));
        assertSame(config.mailing(), injector.getInstance(Mailing.class));
        assertSame(config.demo(), injector.getInstance(Demo.class));
        assertSame(config.discord().oauth(), injector.getInstance(OAuth.class));
    }

    /**
     * The pools are the reason. A second {@link Threading} is a second set of threads that nothing
     * shuts down.
     */
    @Test
    @DisplayName("The thread pools are held once")
    void threadingIsASingleton() {
        Injector injector = injector();

        assertSame(injector.getInstance(Threading.class), injector.getInstance(Threading.class));
    }

    @Test
    @DisplayName("Data can be built, without touching a database")
    void dataIsBuildable() {
        Injector injector = injector();

        Data data = injector.getInstance(Data.class);

        assertNotNull(data);
    }

    /**
     * The graph has a circle in it - the bot needs the commands, the commands need the data access,
     * and the data access needs a gateway that only the bot can give. A lazy {@code Provider} is what
     * makes that a sequence instead, and this is what says it still does.
     */
    @Test
    @DisplayName("Every core can be built, bot and all, without the circle closing")
    void coresAreBuildable() {
        Injector injector = injector();

        assertNotNull(injector.getInstance(Data.class));
        assertNotNull(injector.getInstance(MailingService.class));
        assertNotNull(injector.getInstance(DemoService.class));
        assertNotNull(injector.getInstance(WebService.class));
        assertNotNull(injector.getInstance(Bot.class));
        assertNotNull(injector.getInstance(DemoSchedule.class));
    }

    @Test
    @DisplayName("With the bot switched off the gateway is nobody, and the roles are left alone")
    void withoutABotTheGatewayIsNone() {
        Injector injector = injector();

        assertSame(Gateway.NONE, injector.getInstance(Gateway.class));
        assertSame(RoleSync.NOOP, injector.getInstance(RoleSync.class));
    }

    @Test
    @DisplayName("With the bot switched on the gateway is a real one, built without connecting")
    void withABotTheGatewayIsReal() {
        Injector injector = Guice.createInjector(new LynaModule(TestConf.from("""
                auth:
                  jwtSecret: "a-secret-long-enough-to-sign-with-000000"
                baseSettings:
                  botEnabled: true
                """)));

        Gateway gateway = injector.getInstance(Gateway.class);

        assertNotSame(Gateway.NONE, gateway);
        assertNotSame(RoleSync.NOOP, injector.getInstance(RoleSync.class));
    }

    /**
     * What {@code Data#inject} used to do once the bot had connected. Asking the graph for it instead
     * means it cannot be forgotten, and cannot happen twice.
     */
    @Test
    @DisplayName("Guilds is told what keeps its roles in step, without anybody telling it afterwards")
    void guildsKnowsItsRoleSync() {
        Injector injector = injector();

        assertSame(
                injector.getInstance(RoleSync.class),
                injector.getInstance(Guilds.class).roles());
    }

    /**
     * The web layer used to be a tree where each part held its parent, so a class reached the
     * configuration by walking up to whoever owned it. Those references are gone, which is what lets
     * an injector build the thing at all - a parent reference is a circle.
     */
    @Test
    @DisplayName("Every part of the web layer can be built on its own")
    void webLayerIsBuildable() {
        Injector injector = injector();

        for (Class<?> part : List.of(
                WebService.class,
                de.chojo.lyna.web.api.Api.class,
                V1.class,
                Download.class,
                Proxy.class,
                Direct.class,
                Update.class,
                KoFiApi.class,
                Releases.class,
                Wizard.class,
                DemoApi.class,
                Account.class,
                Admin.class,
                Theme.class,
                de.chojo.lyna.web.api.auth.Auth.class)) {
            assertNotNull(injector.getInstance(part), part.getSimpleName() + " could not be built");
        }
    }

    @Test
    @DisplayName("The one-time download links are minted by the same proxy that serves them")
    void proxyIsSharedWithTheBot() {
        Injector injector = injector();

        assertSame(injector.getInstance(Proxy.class), injector.getInstance(Proxy.class));
    }

    /**
     * {@code Bot#initInteractions} used to name every command in a list it built by hand. Collecting
     * them means a command is registered by being bound, and a new one cannot be written and then
     * forgotten - but it also means nothing names them out loud any more, so this counts them.
     */
    @Test
    @DisplayName("Every slash command is collected, and each is a distinct one")
    void commandsAreCollected() {
        Injector injector = injector();

        Set<SlashProvider<Slash>> commands =
                injector.getInstance(Key.get(new TypeLiteral<Set<SlashProvider<Slash>>>() {}));

        assertEquals(11, commands.size());
        assertEquals(
                11,
                commands.stream().map(c -> c.getClass().getName()).distinct().count());
    }
}
