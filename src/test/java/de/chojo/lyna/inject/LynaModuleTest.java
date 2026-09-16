package de.chojo.lyna.inject;

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.configuration.TestConf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private Injector injector() {
        return Guice.createInjector(new LynaModule(TestConf.defaults()));
    }

    @Test
    @DisplayName("The module can be built at all")
    void moduleIsCreatable() {
        assertNotNull(injector());
    }

    @Test
    @DisplayName("The configuration is reachable, and is the one the module was given")
    void configurationIsBound() {
        Conf conf = TestConf.defaults();
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
}
