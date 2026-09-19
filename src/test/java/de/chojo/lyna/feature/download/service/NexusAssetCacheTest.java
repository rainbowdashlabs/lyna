/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.service;

import de.chojo.nexus.entities.AssetXO;
import de.chojo.nexus.entities.MavenMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How often Nexus is asked, and what is served when it cannot answer.
 */
class NexusAssetCacheTest {
    private static final NexusAssetCache.Coordinates PAPER =
            new NexusAssetCache.Coordinates("releases", "de.eldoria", "plugin-paper", "all");
    private static final NexusAssetCache.Coordinates SPIGOT =
            new NexusAssetCache.Coordinates("releases", "de.eldoria", "plugin-spigot", "all");

    private final AtomicLong nanos = new AtomicLong();
    private final List<String> searches = new ArrayList<>();
    private final AtomicReference<List<AssetXO>> nexusHolds = new AtomicReference<>();
    private final AtomicReference<RuntimeException> nexusFails = new AtomicReference<>();

    private NexusAssetCache cache;

    @BeforeEach
    void setUp() {
        nexusHolds.set(List.of(asset("1.1.0"), asset("1.0.0")));
        cache = new NexusAssetCache(
                (coordinates, version) -> {
                    searches.add(coordinates.artifactId() + (version == null ? "" : "@" + version));
                    if (nexusFails.get() != null) throw nexusFails.get();
                    if (version != null) return List.of(asset(version));
                    return nexusHolds.get();
                },
                nanos::get,
                Runnable::run);
    }

    private static AssetXO asset(String version) {
        MavenMeta meta = mock(MavenMeta.class);
        when(meta.version()).thenReturn(version);
        AssetXO asset = mock(AssetXO.class);
        when(asset.maven2()).thenReturn(meta);
        return asset;
    }

    private static List<String> versions(List<AssetXO> assets) {
        return assets.stream().map(asset -> asset.maven2().version()).toList();
    }

    private void wait(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    @DisplayName("A second look at the same jars does not ask Nexus again")
    void secondLookupIsCached() {
        cache.latest(PAPER);
        cache.latest(PAPER);

        assertEquals(List.of("plugin-paper"), searches);
    }

    @Test
    @DisplayName("Jars elsewhere are asked for separately, so a download pointed elsewhere is fresh")
    void otherCoordinatesAreTheirOwnEntry() {
        cache.latest(PAPER);
        cache.latest(SPIGOT);

        assertEquals(List.of("plugin-paper", "plugin-spigot"), searches);
    }

    @Test
    @DisplayName("After a while the list is refreshed, and the new release shows up")
    void staleListIsRefreshed() {
        cache.latest(PAPER);
        nexusHolds.set(List.of(asset("1.2.0"), asset("1.1.0"), asset("1.0.0")));
        wait(NexusAssetCache.REFRESH.plusSeconds(1));

        cache.latest(PAPER);

        assertEquals(List.of("1.2.0", "1.1.0", "1.0.0"), versions(cache.latest(PAPER)));
    }

    @Test
    @DisplayName("While Nexus cannot be reached, the last list it gave keeps being served")
    void failedRefreshKeepsTheOldList() {
        cache.latest(PAPER);
        nexusFails.set(new IllegalStateException("Nexus is down"));
        wait(NexusAssetCache.REFRESH.plusSeconds(1));

        assertEquals(List.of("1.1.0", "1.0.0"), versions(cache.latest(PAPER)));
        assertEquals(List.of("1.1.0", "1.0.0"), versions(cache.latest(PAPER)));
    }

    @Test
    @DisplayName("An old list is not served forever: after an hour Nexus has to answer")
    void expiredListIsNotServed() {
        cache.latest(PAPER);
        nexusFails.set(new IllegalStateException("Nexus is down"));
        wait(NexusAssetCache.EXPIRE.plusSeconds(1));

        assertThrows(IllegalStateException.class, () -> cache.latest(PAPER));
    }

    @Test
    @DisplayName("A version in the list is answered from it")
    void knownVersionComesFromTheList() {
        cache.latest(PAPER);

        assertEquals(
                "1.0.0", cache.byVersion(PAPER, "1.0.0").orElseThrow().maven2().version());
        assertEquals(List.of("plugin-paper"), searches);
    }

    @Test
    @DisplayName("The newest is the first of the list")
    void latestIsTheNewest() {
        assertEquals(
                "1.1.0", cache.byVersion(PAPER, "latest").orElseThrow().maven2().version());
    }

    @Test
    @DisplayName("A version the list does not hold is still found, by asking Nexus for it")
    void unknownVersionAsksNexus() {
        assertEquals(
                "0.9.0", cache.byVersion(PAPER, "0.9.0").orElseThrow().maven2().version());
        assertTrue(searches.contains("plugin-paper@0.9.0"));
    }
}
