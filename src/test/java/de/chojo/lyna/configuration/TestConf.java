package de.chojo.lyna.configuration;

import java.io.IOException;
import java.nio.file.Files;

/**
 * A real configuration, on a directory of its own.
 *
 * <p>Tests used to mock {@code Configuration} and stub one getter at a time, which meant they agreed
 * with whatever the test assumed rather than with the defaults the application actually starts on. A
 * fresh directory gives the real thing, and a value a test cares about is set by writing it.
 */
public final class TestConf {
    private TestConf() {
    }

    /**
     * @return a configuration holding nothing but the defaults
     */
    public static Conf defaults() {
        return from("{}");
    }

    /**
     * @param yaml what the configuration file says
     * @return a configuration read from exactly that
     */
    public static Conf from(String yaml) {
        try {
            var directory = Files.createTempDirectory("lyna-conf");
            Files.writeString(directory.resolve("config.yaml"), yaml);
            return new Conf(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not make a directory to hold a test configuration", e);
        }
    }
}
