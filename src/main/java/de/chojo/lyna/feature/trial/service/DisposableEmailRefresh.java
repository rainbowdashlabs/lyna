/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.trial.service;

import com.google.inject.Inject;
import de.chojo.lyna.core.Threading;

import java.util.concurrent.TimeUnit;

/**
 * Fetches the current throwaway mail list shortly after start and then daily. The copy in the jar
 * serves until the first refresh lands, and whenever one fails.
 */
public class DisposableEmailRefresh {
    private final Threading threading;
    private final DisposableEmailDomains domains;

    @Inject
    public DisposableEmailRefresh(Threading threading, DisposableEmailDomains domains) {
        this.threading = threading;
        this.domains = domains;
    }

    public void start() {
        threading.botWorker().scheduleAtFixedRate(domains::refresh, 5, 24 * 60, TimeUnit.MINUTES);
    }
}
