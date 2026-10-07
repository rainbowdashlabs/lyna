/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository.TimeChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The channels whose name shows the time somewhere.
 */
class TimeChannelRepositoryTest extends RepositoryTestBase {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final TimeChannelRepository channels = new TimeChannelRepository();

    @BeforeEach
    void clearChannels() throws SQLException {
        clear("time_channel");
    }

    @Test
    @DisplayName("A channel set up is found in its guild, and only there")
    void foundInItsGuild() {
        var channel = new TimeChannel(1L, 10L, BERLIN, "Dev {time}");
        channels.set(channel);

        assertEquals(List.of(channel), channels.inGuild(1L));
        assertEquals(List.of(), channels.inGuild(2L));
        assertEquals(List.of(channel), channels.all());
    }

    @Test
    @DisplayName("Setting a channel again changes its zone and name instead of adding it twice")
    void settingAgainReplaces() {
        channels.set(new TimeChannel(1L, 10L, BERLIN, "Dev {time}"));
        channels.set(new TimeChannel(1L, 10L, ZoneId.of("UTC"), "UTC {time}"));

        assertEquals(List.of(new TimeChannel(1L, 10L, ZoneId.of("UTC"), "UTC {time}")), channels.all());
    }

    @Test
    @DisplayName("A channel is removed only through its own guild")
    void removedThroughItsGuild() {
        channels.set(new TimeChannel(1L, 10L, BERLIN, "Dev {time}"));

        assertFalse(channels.remove(2L, 10L));
        assertTrue(channels.remove(1L, 10L));
        assertFalse(channels.remove(1L, 10L));
        assertTrue(channels.all().isEmpty());
    }
}
