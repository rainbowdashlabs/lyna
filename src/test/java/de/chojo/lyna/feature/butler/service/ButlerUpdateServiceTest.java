/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.butler.service;

import de.chojo.lyna.feature.butler.repository.ButlerApplicationRepository;
import de.chojo.lyna.feature.butler.service.ButlerUpdateService.CheckAnswer;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.repository.Downloads;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.nexus.entities.AssetXO;
import de.chojo.nexus.entities.Checksum;
import de.chojo.nexus.entities.MavenMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a plugin built against UpdateButler is told, and which file it is then given.
 */
class ButlerUpdateServiceTest {
    private final Map<ReleaseType, List<Download>> byType = new EnumMap<>(ReleaseType.class);
    private final Product product = mock(Product.class);
    private ButlerUpdateService service;

    @BeforeEach
    void setUp() {
        Downloads downloads = mock(Downloads.class);
        when(product.downloads()).thenReturn(downloads);
        for (ReleaseType type : ReleaseType.values()) {
            byType.put(type, new ArrayList<>());
            when(downloads.byReleaseType(type)).thenAnswer(invocation -> byType.get(type));
        }
        ButlerApplicationRepository applications = mock(ButlerApplicationRepository.class);
        when(applications.productFor(5)).thenReturn(Optional.of(42));
        ProductLookup products = mock(ProductLookup.class);
        when(products.byId(42)).thenReturn(Optional.of(product));
        service = new ButlerUpdateService(applications, products);
    }

    private Download download(ReleaseType type, AssetXO... newestFirst) {
        Download download = mock(Download.class);
        when(download.latestAssets()).thenReturn(List.of(newestFirst));
        when(download.assetByVersion(anyString())).thenReturn(Optional.empty());
        for (AssetXO asset : newestFirst) {
            when(download.assetByVersion(asset.maven2().version())).thenReturn(Optional.of(asset));
        }
        byType.get(type).add(download);
        return download;
    }

    private static AssetXO asset(String version, int day) {
        MavenMeta meta = mock(MavenMeta.class);
        when(meta.version()).thenReturn(version);
        AssetXO asset = mock(AssetXO.class);
        when(asset.maven2()).thenReturn(meta);
        when(asset.lastModified()).thenReturn(OffsetDateTime.of(2026, 1, day, 0, 0, 0, 0, ZoneOffset.UTC));
        when(asset.checksum()).thenReturn(new Checksum("sha1", "sha256-" + version, "sha512", "md5"));
        return asset;
    }

    @Test
    @DisplayName("A butler id finds the product it was mapped to, and an unmapped one finds nothing")
    void productByButlerId() {
        assertEquals(Optional.of(product), service.product(5));
        assertTrue(service.product(6).isEmpty());
    }

    @Test
    @DisplayName("An older stable build is told about the newest stable, with its Nexus sha256")
    void olderStableIsTold() {
        download(ReleaseType.STABLE, asset("1.3.0", 3), asset("1.2.0", 2));
        download(ReleaseType.DEV, asset("1.4.0-DEV", 4));

        assertEquals(
                Optional.of(new CheckAnswer(true, "1.3.0", "sha256-1.3.0")),
                service.check(product, "1.2.0", false));
    }

    @Test
    @DisplayName("The newest stable build is told there is nothing newer")
    void newestStableIsNotTold() {
        download(ReleaseType.STABLE, asset("1.3.0", 3));

        assertEquals(
                Optional.of(new CheckAnswer(false, "1.3.0", "sha256-1.3.0")),
                service.check(product, "1.3.0", false));
    }

    @Test
    @DisplayName("Asking for dev builds includes them")
    void devFlagIncludesDev() {
        download(ReleaseType.STABLE, asset("1.3.0", 3));
        download(ReleaseType.DEV, asset("1.4.0-DEV", 4));

        assertEquals("1.4.0-DEV", service.check(product, "1.3.0", true).orElseThrow().latestVersion());
    }

    @Test
    @DisplayName("A dev build follows dev even when the client sends no flag")
    void runningDevFollowsDev() {
        download(ReleaseType.STABLE, asset("1.3.0", 3));
        download(ReleaseType.DEV, asset("1.4.0-DEV", 4));

        var answer = service.check(product, "1.3.5-DEV", false).orElseThrow();

        assertEquals("1.4.0-DEV", answer.latestVersion());
        assertTrue(answer.newVersionAvailable());
    }

    @Test
    @DisplayName("A stable release overtakes the dev build it came from")
    void stableOvertakesDev() {
        download(ReleaseType.STABLE, asset("1.4.0", 5));
        download(ReleaseType.DEV, asset("1.4.0-DEV", 4));

        assertEquals("1.4.0", service.check(product, "1.4.0-DEV", false).orElseThrow().latestVersion());
    }

    @Test
    @DisplayName("Several downloads of one track are compared, not just the first")
    void allDownloadsOfATrack() {
        download(ReleaseType.STABLE, asset("1.2.0", 2));
        download(ReleaseType.STABLE, asset("1.3.0", 3));

        assertEquals("1.3.0", service.check(product, "1.0.0", false).orElseThrow().latestVersion());
    }

    @Test
    @DisplayName("A product without a build on the track has no answer")
    void noBuildNoAnswer() {
        download(ReleaseType.DEV, asset("1.4.0-DEV", 4));

        assertTrue(service.check(product, "1.0.0", false).isEmpty());
    }

    @Test
    @DisplayName("A version is found in whichever download holds it, stable first")
    void buildByVersion() {
        Download stable = download(ReleaseType.STABLE, asset("1.3.0", 3));
        Download dev = download(ReleaseType.DEV, asset("1.4.0-DEV", 4));

        assertEquals(stable, service.build(product, "1.3.0").orElseThrow().download());
        assertEquals(dev, service.build(product, "1.4.0-DEV").orElseThrow().download());
        assertTrue(service.build(product, "9.9.9").isEmpty());
    }
}
