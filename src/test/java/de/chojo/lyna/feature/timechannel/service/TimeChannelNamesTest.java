/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.timechannel.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a time channel is called, and when it is renamed next.
 */
class TimeChannelNamesTest {
    private static final Instant NOON_UTC = Instant.parse("2026-07-01T12:07:30Z");

    @Test
    @DisplayName("The time goes where the template says, in the channel's zone")
    void timeInZone() {
        assertEquals("Developer Time: 14:07",
                TimeChannelNames.name(TimeChannelNames.DEFAULT_TEMPLATE, ZoneId.of("Europe/Berlin"), NOON_UTC));
        assertEquals("NYC 08:07", TimeChannelNames.name("NYC {time}", ZoneId.of("America/New_York"), NOON_UTC));
    }

    @Test
    @DisplayName("A name longer than Discord allows is cut")
    void longNameIsCut() {
        assertEquals(100, TimeChannelNames.name("x".repeat(200) + "{time}", ZoneId.of("UTC"), NOON_UTC).length());
    }

    @Test
    @DisplayName("The next rename is on the next quarter of an hour")
    void nextQuarter() {
        assertEquals(Duration.ofSeconds(7 * 60 + 30), TimeChannelNames.untilNextRename(NOON_UTC));
        assertEquals(Duration.ofMinutes(15), TimeChannelNames.untilNextRename(Instant.parse("2026-07-01T12:45:00Z")));
        assertEquals(Duration.ofSeconds(1), TimeChannelNames.untilNextRename(Instant.parse("2026-07-01T12:59:59Z")));
    }

    @Test
    @DisplayName("Zones are offered by what was typed, shortest first")
    void zonesMatching() {
        var zones = TimeChannelNames.zonesMatching("berlin", 25);

        assertEquals("Europe/Berlin", zones.getFirst());
        assertTrue(TimeChannelNames.zonesMatching("", 25).size() <= 25);
    }
}
