/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.service.UpdateChannels;
import de.chojo.lyna.feature.download.service.UpdateChannels.Build;
import de.chojo.lyna.feature.download.service.UpdateChannels.Decision;
import de.chojo.lyna.util.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which channels an installed build hears of, and which build it is told to update to.
 */
class UpdateChannelsTest {
    private static final Instant EARLIER = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-10T00:00:00Z");

    private static Decision decide(String current, Instant created, Map<ReleaseType, Build> latest) {
        return UpdateChannels.decide(Version.parse(current), created, type -> Optional.ofNullable(latest.get(type)));
    }

    private static Map<ReleaseType, Build> channels(Build stable, Build dev, Build snapshot) {
        Map<ReleaseType, Build> map = new EnumMap<>(ReleaseType.class);
        if (stable != null) map.put(ReleaseType.STABLE, stable);
        if (dev != null) map.put(ReleaseType.DEV, dev);
        if (snapshot != null) map.put(ReleaseType.SNAPSHOT, snapshot);
        return map;
    }

    private static String version(Decision decision) {
        return decision.build().orElseThrow().version();
    }

    @Test
    @DisplayName("A stable build hears only of newer stable builds")
    void stableHearsStable() {
        var latest = channels(
                new Build("1.0.0", EARLIER), new Build("1.2.0-DEV", LATER), new Build("1.3.0-SNAPSHOT", LATER));

        Decision decision = decide("1.0.0", EARLIER, latest);

        assertFalse(decision.update());
        assertEquals("1.0.0", version(decision));

        Decision newer =
                decide("1.0.0", EARLIER, channels(new Build("1.1.0", LATER), new Build("1.2.0-DEV", LATER), null));
        assertTrue(newer.update());
        assertEquals("1.1.0", version(newer));
    }

    @Test
    @DisplayName("A dev build hears of newer stable and dev builds, never of snapshots")
    void devHearsStableAndDev() {
        Decision dev = decide(
                "1.0.0-DEV",
                EARLIER,
                channels(
                        new Build("0.9.0", EARLIER),
                        new Build("1.1.0-DEV", LATER),
                        new Build("1.5.0-SNAPSHOT", LATER)));
        assertTrue(dev.update());
        assertEquals("1.1.0-DEV", version(dev));

        Decision stable = decide(
                "1.0.0-DEV", EARLIER, channels(new Build("1.0.0", LATER), new Build("1.0.0-DEV", EARLIER), null));
        assertTrue(stable.update(), "the release of a dev version is newer than the dev build");
        assertEquals("1.0.0", version(stable));

        Decision snapshotOnly = decide(
                "1.0.0-DEV",
                EARLIER,
                channels(null, new Build("1.0.0-DEV", EARLIER), new Build("2.0.0-SNAPSHOT", LATER)));
        assertFalse(snapshotOnly.update());
    }

    @Test
    @DisplayName("A snapshot build hears of anything newer, a dev build even when no snapshot is")
    void snapshotHearsEverything() {
        Decision dev = decide(
                "1.0.0-SNAPSHOT",
                EARLIER,
                channels(
                        new Build("0.9.0", EARLIER),
                        new Build("1.1.0-DEV", LATER),
                        new Build("1.0.0-SNAPSHOT", EARLIER)));
        assertTrue(dev.update());
        assertEquals("1.1.0-DEV", version(dev));

        Decision noSnapshotChannel =
                decide("1.0.0-SNAPSHOT", EARLIER, channels(null, new Build("1.1.0-DEV", LATER), null));
        assertTrue(noSnapshotChannel.update());

        Decision snapshot = decide(
                "1.0.0-SNAPSHOT",
                EARLIER,
                channels(
                        new Build("1.0.0", EARLIER),
                        new Build("1.1.0-DEV", LATER),
                        new Build("1.2.0-SNAPSHOT", LATER)));
        assertEquals("1.2.0-SNAPSHOT", version(snapshot), "the newest of all channels wins");
    }

    @Test
    @DisplayName("A dev or snapshot rebuilt under the same version is an update, a stable one is not")
    void rebuilt() {
        assertTrue(decide("1.0.0-DEV", EARLIER, channels(null, new Build("1.0.0-DEV", LATER), null))
                .update());
        assertFalse(decide("1.0.0-DEV", LATER, channels(null, new Build("1.0.0-DEV", EARLIER), null))
                .update());
        assertFalse(
                decide("1.0.0-DEV", null, channels(null, new Build("1.0.0-DEV", LATER), null))
                        .update(),
                "without a build time a rebuild cannot be told apart");
        assertFalse(decide("1.0.0", EARLIER, channels(new Build("1.0.0", LATER), null, null))
                .update());
    }

    @Test
    @DisplayName("A product publishing nothing the build hears of names no build")
    void nothingPublished() {
        Decision decision = decide("1.0.0", EARLIER, channels(null, new Build("2.0.0-DEV", LATER), null));

        assertFalse(decision.update());
        assertTrue(decision.build().isEmpty());
    }
}
