/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.timechannel.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What a time channel is called at a given moment, and when it is next renamed.
 *
 * <p>Discord lets a channel be renamed twice in ten minutes, so the name changes every quarter of an
 * hour, on the quarter, and shows the time to the minute.
 */
public final class TimeChannelNames {
    public static final String TIME = "{time}";
    public static final String DEFAULT_TEMPLATE = "Developer Time: " + TIME;
    public static final Duration INTERVAL = Duration.ofMinutes(15);
    private static final int MAX_NAME_LENGTH = 100;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private TimeChannelNames() {
    }

    public static String name(String template, ZoneId zone, Instant now) {
        String name = template.replace(TIME, CLOCK.format(now.atZone(zone)));
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    /**
     * @return how long until the next quarter of an hour
     */
    public static Duration untilNextRename(Instant now) {
        Instant hour = now.truncatedTo(ChronoUnit.HOURS);
        long quarters = Duration.between(hour, now).toMillis() / INTERVAL.toMillis() + 1;
        return Duration.between(now, hour.plus(INTERVAL.multipliedBy(quarters)));
    }

    /**
     * @return the zones whose id contains what was typed, for autocompletion
     */
    public static List<String> zonesMatching(String typed, int limit) {
        String needle = typed.toLowerCase(Locale.ROOT);
        return ZoneId.getAvailableZoneIds().stream()
                .filter(zone -> zone.toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                .limit(limit)
                .toList();
    }
}
