package de.chojo.lyna.inject;

import com.google.inject.Guice;
import com.google.inject.Injector;
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
import de.chojo.lyna.core.Web;
import de.chojo.lyna.data.roles.RoleSync;
import de.chojo.lyna.demo.DemoSchedule;
import de.chojo.lyna.demo.DemoService;
import de.chojo.lyna.gateway.Gateway;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.core.Threading;
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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        for (Class<?> dao : List.of(Accounts.class, AccountLicenses.class, LicenseInvites.class,
                AccountSessions.class, RevokedJtis.class, DownloadLog.class, DemoArtifacts.class,
                InstanceSettingsAccess.class, InstanceOperators.class, PasswordResetTokens.class,
                EmailVerificationTokens.class, KioskProducts.class, Guilds.class, Products.class,
                Mailings.class, KoFiProducts.class)) {
            assertNotNull(injector.getInstance(dao), dao.getSimpleName() + " could not be built");
            assertSame(injector.getInstance(dao), injector.getInstance(dao),
                    dao.getSimpleName() + " should be held once");
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
        assertSame(data.accounts(), injector.getInstance(Accounts.class));
        assertSame(data.guilds(), injector.getInstance(Guilds.class));
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
        assertNotNull(injector.getInstance(Web.class));
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

        assertSame(injector.getInstance(RoleSync.class), injector.getInstance(Guilds.class).roles());
    }
}
