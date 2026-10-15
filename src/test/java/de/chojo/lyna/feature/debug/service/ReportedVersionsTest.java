/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.service;

import de.chojo.lyna.feature.debug.service.ReportedVersions.Latest;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.repository.Downloads;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.nexus.entities.AssetXO;
import de.chojo.nexus.entities.MavenMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a debug report's viewer is told about the version it came from.
 */
class ReportedVersionsTest {
    private final ProductLookup products = mock(ProductLookup.class);
    private final Product product = mock(Product.class);
    private ReportedVersions versions;

    @BeforeEach
    void setUp() {
        when(products.byPluginName("BloodNight")).thenReturn(Optional.of(product));
        when(products.byPluginName("Unknown")).thenReturn(Optional.empty());
        Downloads downloads = mock(Downloads.class);
        when(product.downloads()).thenReturn(downloads);
        List<Download> stable = List.of(download("2.1.0", "2.0.0"));
        List<Download> dev = List.of(download("2.2.0-DEV"));
        when(downloads.byReleaseType(ReleaseType.STABLE)).thenReturn(stable);
        when(downloads.byReleaseType(ReleaseType.DEV)).thenReturn(dev);
        when(downloads.byReleaseType(ReleaseType.SNAPSHOT)).thenReturn(List.of());
        versions = new ReportedVersions(products);
    }

    private static Download download(String... newestFirst) {
        List<AssetXO> assets = new ArrayList<>();
        for (String version : newestFirst) {
            MavenMeta meta = mock(MavenMeta.class);
            when(meta.version()).thenReturn(version);
            AssetXO asset = mock(AssetXO.class);
            when(asset.maven2()).thenReturn(meta);
            assets.add(asset);
        }
        Download download = mock(Download.class);
        when(download.latestAssets()).thenReturn(assets);
        return download;
    }

    @Test
    @DisplayName("An older stable version is outdated against the latest stable one")
    void outdated() {
        assertEquals(Optional.of(new Latest("2.1.0", true)), versions.latest("BloodNight", "2.0.0"));
    }

    @Test
    @DisplayName("The latest version is not outdated")
    void current() {
        assertEquals(Optional.of(new Latest("2.1.0", false)), versions.latest("BloodNight", "2.1.0"));
    }

    @Test
    @DisplayName("A dev build is compared with stable and dev builds")
    void devTrack() {
        assertEquals(Optional.of(new Latest("2.2.0-DEV", true)), versions.latest("BloodNight", "2.1.0-DEV"));
    }

    @Test
    @DisplayName("A plugin that is no product here gets no hint")
    void unknownPlugin() {
        assertTrue(versions.latest("Unknown", "1.0.0").isEmpty());
    }

    @Test
    @DisplayName("A failing lookup gives no hint rather than an error")
    void failingLookup() {
        when(products.byPluginName("Broken")).thenThrow(new IllegalStateException("Nexus is down"));
        assertTrue(versions.latest("Broken", "1.0.0").isEmpty());
    }

    @Test
    @DisplayName("Nexus is asked once per plugin and track, then the answer is kept")
    void cached() {
        versions.latest("BloodNight", "2.0.0");
        versions.latest("BloodNight", "1.0.0");
        verify(product, times(1)).downloads();
    }
}
