package de.chojo.lyna.configuration.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.Prop;
import dev.chojo.ocular.override.OverwritePrefix;


/**
 * Demo mode: an instance that seeds itself with something to look at, and lets anybody sign in as
 * one of the accounts it made.
 *
 * <p>Off unless asked for, and asked for in two places - the configuration file, or
 * {@code DEMO_ENABLED} - because a demo is usually a container somebody started rather than a file
 * somebody edited.
 *
 * <p><strong>Never turn this on for an instance holding real data.</strong> Signing in needs no
 * password, and the reset deletes what the seed made.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("DEMO")
public class Demo {
    @Overwrite(env = @Env, prop = @Prop)
    private boolean enabled = false;
    @Overwrite(env = @Env, prop = @Prop)
    private int resetIntervalMinutes = 60;

    public boolean enabled() {
        return enabled;
    }

    /**
     * @return how often the data is thrown away and seeded again; zero or less never resets on its own
     */
    public int resetIntervalMinutes() {
        return resetIntervalMinutes;
    }
}
