/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.core.Threading;
import de.chojo.lyna.feature.account.service.DiscordConnectionService;
import de.chojo.lyna.feature.account.service.DiscordTokenRefresher;
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
 * The hourly run that keeps Discord grants alive.
 */
class DiscordTokenRefresherTest {

    @Test
    @DisplayName("It runs hourly on the worker pool, and a failing run does not end the schedule")
    void runsHourlyAndSurvivesFailures() {
        Threading threading = mock(Threading.class);
        ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
        when(threading.botWorker()).thenReturn(worker);
        DiscordConnectionService connections = mock(DiscordConnectionService.class);
        when(connections.refreshExpiring()).thenReturn(2).thenThrow(new IllegalStateException("database gone"));

        new DiscordTokenRefresher(threading, connections).start();

        ArgumentCaptor<Runnable> run = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).scheduleAtFixedRate(run.capture(), eq(1L), eq(60L), eq(TimeUnit.MINUTES));
        run.getValue().run();
        assertDoesNotThrow(() -> run.getValue().run());
        verify(connections, times(2)).refreshExpiring();
    }

    @Test
    @DisplayName("A run with nothing due passes quietly")
    void nothingDue() {
        Threading threading = mock(Threading.class);
        when(threading.botWorker()).thenReturn(mock(ScheduledExecutorService.class));
        DiscordConnectionService connections = mock(DiscordConnectionService.class);

        new DiscordTokenRefresher(threading, connections).start();

        ArgumentCaptor<Runnable> run = ArgumentCaptor.forClass(Runnable.class);
        verify(threading.botWorker()).scheduleAtFixedRate(run.capture(), eq(1L), eq(60L), eq(TimeUnit.MINUTES));
        assertDoesNotThrow(() -> run.getValue().run());
        verify(connections).refreshExpiring();
    }
}
