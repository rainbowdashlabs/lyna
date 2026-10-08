/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.trial.service;

import de.chojo.lyna.core.Threading;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The daily refresh of the throwaway mail list.
 */
class DisposableEmailRefreshTest {

    @Test
    @DisplayName("The list is refreshed shortly after start and then once a day")
    void scheduled() {
        ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
        Threading threading = mock(Threading.class);
        when(threading.botWorker()).thenReturn(worker);
        DisposableEmailDomains domains = mock(DisposableEmailDomains.class);

        new DisposableEmailRefresh(threading, domains).start();

        ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).scheduleAtFixedRate(job.capture(), eq(5L), eq(24L * 60), eq(TimeUnit.MINUTES));
        job.getValue().run();
        verify(domains).refresh();
    }
}
