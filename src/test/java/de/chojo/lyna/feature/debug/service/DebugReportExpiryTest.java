/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.service;

import de.chojo.lyna.core.Threading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The hourly job that deletes debug reports past their retention.
 */
class DebugReportExpiryTest {
    private final ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
    private final DebugReportService reports = mock(DebugReportService.class);
    private Runnable job;

    @BeforeEach
    void start() {
        Threading threading = mock(Threading.class);
        when(threading.botWorker()).thenReturn(worker);
        new DebugReportExpiry(threading, reports).start();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).scheduleAtFixedRate(captor.capture(), eq(1L), eq(60L), eq(TimeUnit.MINUTES));
        job = captor.getValue();
    }

    @Test
    @DisplayName("Each run expires what is due, whether or not anything was")
    void expires() {
        when(reports.expire()).thenReturn(3, 0);

        job.run();
        job.run();

        verify(reports, times(2)).expire();
    }

    @Test
    @DisplayName("A run that fails does not take the schedule down with it")
    void failureIsCaught() {
        when(reports.expire()).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(job::run);
    }
}
