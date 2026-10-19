/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.service;

import com.google.inject.Inject;
import de.chojo.lyna.core.Threading;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Refreshes the Discord grants that are about to run out, hourly.
 *
 * <p>A run that throws is caught: a scheduled task that fails is never run again, and every grant
 * would then quietly expire.
 */
public class DiscordTokenRefresher {
    private static final Logger log = getLogger(DiscordTokenRefresher.class);

    private final Threading threading;
    private final DiscordConnectionService connections;

    @Inject
    public DiscordTokenRefresher(Threading threading, DiscordConnectionService connections) {
        this.threading = threading;
        this.connections = connections;
    }

    public void start() {
        threading.botWorker().scheduleAtFixedRate(this::refresh, 1, 60, TimeUnit.MINUTES);
    }

    void refresh() {
        try {
            int refreshed = connections.refreshExpiring();
            if (refreshed > 0) log.info("Refreshed {} Discord grants", refreshed);
        } catch (Exception e) {
            log.error("Could not refresh the Discord grants", e);
        }
    }
}
