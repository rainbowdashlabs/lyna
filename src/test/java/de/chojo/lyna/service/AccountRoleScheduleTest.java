/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.core.Threading;
import de.chojo.lyna.feature.account.service.AccountRoleSchedule;
import de.chojo.lyna.feature.account.service.AccountRoleSync;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The periodic role sync, and the one run when the gateway connects.
 */
class AccountRoleScheduleTest {
    private final Threading threading = mock(Threading.class);
    private final ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
    private final AccountRoleSync sync = mock(AccountRoleSync.class);

    @Test
    @DisplayName("It runs every six hours on the worker pool, and a failing run does not end the schedule")
    void runsEverySixHours() {
        when(threading.botWorker()).thenReturn(worker);
        doThrow(new IllegalStateException("database gone"))
                .doNothing()
                .when(sync)
                .syncAll();

        new AccountRoleSchedule(threading, sync).start();

        ArgumentCaptor<Runnable> run = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).scheduleAtFixedRate(run.capture(), eq(6L), eq(6L), eq(TimeUnit.HOURS));
        assertDoesNotThrow(() -> run.getValue().run());
        run.getValue().run();
        verify(sync, times(2)).syncAll();
    }

    @Test
    @DisplayName("A connecting gateway gets one run on the worker pool")
    void runNow() {
        when(threading.botWorker()).thenReturn(worker);

        new AccountRoleSchedule(threading, sync).runNow();

        ArgumentCaptor<Runnable> run = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).execute(run.capture());
        run.getValue().run();
        verify(sync).syncAll();
    }
}
