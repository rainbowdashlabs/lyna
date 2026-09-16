package de.chojo.lyna.inject;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.ConfigFile;

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
}
