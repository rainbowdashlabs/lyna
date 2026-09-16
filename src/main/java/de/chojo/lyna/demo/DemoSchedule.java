package de.chojo.lyna.demo;

import com.google.inject.Inject;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.core.Threading;
import de.chojo.lyna.gateway.Gateway;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Lays the demo data out at startup, and again on a timer.
 *
 * <p>A demo is looked at by people who change things, so it goes back to a known state on its own
 * rather than waiting for somebody to notice it has drifted. The first run happens shortly after
 * startup rather than during it: the seed reads the guild's members, and the gateway has usually not
 * finished telling us about them at the moment the bot reports ready.
 */
public final class DemoSchedule {
    private static final Logger log = getLogger(DemoSchedule.class);
    private static final int FIRST_RUN_DELAY_SECONDS = 30;

    private final Threading threading;
    private final DemoService demo;
    private final Conf configuration;
    private final Gateway gateway;

    @Inject
    public DemoSchedule(Threading threading, DemoService demo, Conf configuration, Gateway gateway) {
        this.threading = threading;
        this.demo = demo;
        this.configuration = configuration;
        this.gateway = gateway;
    }

    /**
     * <p>Does nothing without a gateway. The seed builds its cast from a guild's members, so with no
     * bot connected there is nobody to seed from, and a timer that failed every interval would say so
     * in the log forever.
     */
    public void start() {
        if (!demo.enabled()) return;
        if (!gateway.connected()) {
            log.info("[demo] demo mode is on, but there is no bot connected to seed a guild from.");
            return;
        }
        int minutes = configuration.main().demo().resetIntervalMinutes();
        log.info("[demo] demo mode is on. The data is laid out shortly, {}",
                minutes > 0 ? "and again every %d minutes".formatted(minutes) : "and not reset again on its own");

        threading.botWorker().schedule(this::reseed, FIRST_RUN_DELAY_SECONDS, TimeUnit.SECONDS);
        if (minutes > 0) {
            threading.botWorker().scheduleAtFixedRate(this::reseed,
                    (long) minutes * 60 + FIRST_RUN_DELAY_SECONDS, (long) minutes * 60, TimeUnit.SECONDS);
        }
    }

    /**
     * A seed that throws must not take the scheduler's thread with it: a scheduled task that fails
     * is never run again, so a demo that failed once would stay stale until somebody restarted it.
     */
    private void reseed() {
        try {
            demo.reseed();
        } catch (Exception e) {
            log.error("[demo] could not lay the data out", e);
        }
    }
}
