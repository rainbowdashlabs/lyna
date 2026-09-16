package de.chojo.lyna.configuration.elements;

import de.chojo.jdautil.util.SysVar;

/**
 * Demo mode: an instance that seeds itself with something to look at, and lets anybody sign in as
 * one of the accounts it made.
 *
 * <p>Off unless asked for, and asked for in two places - the configuration file, or
 * {@code LYNA_DEMO_ENABLED} - because a demo is usually a container somebody started rather than a
 * file somebody edited.
 *
 * <p><strong>Never turn this on for an instance holding real data.</strong> Signing in needs no
 * password, and the reset deletes what the seed made.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class Demo {
    private boolean enabled = false;
    private int resetIntervalMinutes = 60;

    public boolean enabled() {
        String override = SysVar.envOrProp("LYNA_DEMO_ENABLED", "lyna.demo.enabled", null);
        if (override != null && !override.isBlank()) return Boolean.parseBoolean(override);
        return enabled;
    }

    /**
     * @return how often the data is thrown away and seeded again; zero or less never resets on its own
     */
    public int resetIntervalMinutes() {
        return resetIntervalMinutes;
    }
}
