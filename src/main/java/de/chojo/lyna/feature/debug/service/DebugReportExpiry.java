/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.service;

import com.google.inject.Inject;
import de.chojo.lyna.core.Threading;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Deletes debug reports once they are older than the retention, hourly.
 *
 * <p>A run that throws is caught: a scheduled task that fails is never run again, and reports would
 * then pile up unseen.
 */
public class DebugReportExpiry {
    private static final Logger log = getLogger(DebugReportExpiry.class);

    private final Threading threading;
    private final DebugReportService reports;

    @Inject
    public DebugReportExpiry(Threading threading, DebugReportService reports) {
        this.threading = threading;
        this.reports = reports;
    }

    public void start() {
        threading.botWorker().scheduleAtFixedRate(this::expire, 1, 60, TimeUnit.MINUTES);
    }

    private void expire() {
        try {
            int expired = reports.expire();
            if (expired > 0) log.info("Deleted {} expired debug reports", expired);
        } catch (Exception e) {
            log.error("Could not delete expired debug reports", e);
        }
    }
}
