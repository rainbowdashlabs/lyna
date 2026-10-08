/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.timechannel.service;

import de.chojo.lyna.core.Threading;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository.TimeChannel;
import de.chojo.lyna.gateway.Gateway;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.managers.channel.ChannelManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Renaming time channels on the quarter hour, through whatever gateway there is.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class TimeChannelScheduleTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
    private final Threading threading = mock(Threading.class);
    private final Gateway gateway = mock(Gateway.class);
    private final TimeChannelRepository channels = mock(TimeChannelRepository.class);
    private final Guild guild = mock(Guild.class);
    private final GuildChannel channel = mock(GuildChannel.class);
    private final ChannelManager manager = mock(ChannelManager.class);
    private TimeChannelSchedule schedule;

    @BeforeEach
    void setUp() {
        when(threading.botWorker()).thenReturn(worker);
        when(gateway.connected()).thenReturn(true);
        when(gateway.guild(1L)).thenReturn(Optional.of(guild));
        when(guild.getGuildChannelById(10L)).thenReturn(channel);
        when(channel.getManager()).thenReturn(manager);
        when(manager.setName(anyString())).thenReturn(manager);
        schedule = new TimeChannelSchedule(threading, gateway, channels);
    }

    private static TimeChannel timeChannel(String template) {
        return new TimeChannel(1L, 10L, UTC, template);
    }

    @Test
    @DisplayName("Without a gateway nothing is scheduled")
    void noGatewayNoSchedule() {
        when(gateway.connected()).thenReturn(false);

        schedule.start();

        verifyNoInteractions(worker);
    }

    @Test
    @DisplayName("With a gateway every channel is renamed on each quarter hour")
    void scheduledRun() {
        when(channel.getName()).thenReturn("old");
        when(channels.all()).thenReturn(List.of(timeChannel("Dev {time}")));

        schedule.start();
        ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).scheduleAtFixedRate(job.capture(), anyLong(), eq(15 * 60 * 1000L), eq(TimeUnit.MILLISECONDS));
        job.getValue().run();

        verify(manager).setName(TimeChannelNames.name("Dev {time}", UTC, Instant.now()));
    }

    @Test
    @DisplayName("A run that fails does not take the schedule down with it")
    void failingRunIsCaught() {
        when(channels.all()).thenThrow(new IllegalStateException("database down"));
        schedule.start();
        ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).scheduleAtFixedRate(job.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));

        assertDoesNotThrow(() -> job.getValue().run());
    }

    @Test
    @DisplayName("A channel already showing the time is left alone")
    void unchangedNameIsNotRenamed() {
        when(channel.getName()).thenReturn("Fixed");

        schedule.refresh(timeChannel("Fixed"));

        verify(manager, never()).setName(anyString());
    }

    @Test
    @DisplayName("A channel the bot cannot see is skipped")
    void invisibleChannelIsSkipped() {
        when(guild.getGuildChannelById(10L)).thenReturn(null);

        schedule.refresh(timeChannel("Dev {time}"));

        verify(manager, never()).setName(anyString());
    }

    @Test
    @DisplayName("A rename Discord refuses is logged rather than thrown")
    void refusedRenameIsLogged() {
        when(channel.getName()).thenReturn("old");

        schedule.refresh(timeChannel("Dev {time}"));

        ArgumentCaptor<Consumer> success = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer> failure = ArgumentCaptor.forClass(Consumer.class);
        verify(manager).queue(success.capture(), failure.capture());
        assertDoesNotThrow(() -> success.getValue().accept(null));
        assertDoesNotThrow(() -> failure.getValue().accept(new RuntimeException("missing permission")));
    }
}
