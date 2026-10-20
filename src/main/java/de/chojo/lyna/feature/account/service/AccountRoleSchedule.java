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
 * Re-reads the roles of every linked account every six hours, and whenever the gateway connects.
 *
 * <p>The backstop for whatever the gateway events missed, most of all what changed while the bot was
 * not connected. A run that throws is caught: a scheduled task that fails is never run again.
 */
public class AccountRoleSchedule {
    private static final Logger log = getLogger(AccountRoleSchedule.class);
    private static final long INTERVAL_HOURS = 6;

    private final Threading threading;
    private final AccountRoleSync sync;

    @Inject
    public AccountRoleSchedule(Threading threading, AccountRoleSync sync) {
        this.threading = threading;
        this.sync = sync;
    }

    public void start() {
        threading.botWorker().scheduleAtFixedRate(this::run, INTERVAL_HOURS, INTERVAL_HOURS, TimeUnit.HOURS);
    }

    /**
     * Runs a sync on the worker pool now, for a gateway that has just connected.
     */
    public void runNow() {
        threading.botWorker().execute(this::run);
    }

    void run() {
        try {
            sync.syncAll();
        } catch (Exception e) {
            log.error("Could not sync the roles of linked accounts", e);
        }
    }
}
