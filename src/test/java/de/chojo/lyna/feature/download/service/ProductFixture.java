/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.service;

import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.DownloadType;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.repository.Downloads;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.nexus.entities.AssetXO;
import de.chojo.nexus.entities.MavenMeta;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A product with download types and the versions Nexus holds for each, built without a guild, a
 * database or a Nexus behind it.
 */
final class ProductFixture {
    final Product product = mock(Product.class);
    private final Downloads downloads = mock(Downloads.class);
    private final List<Download> all = new ArrayList<>();

    ProductFixture(String name) {
        when(product.name()).thenReturn(name);
        when(product.downloads()).thenReturn(downloads);
        when(downloads.downloads()).thenReturn(all);
        for (ReleaseType releaseType : ReleaseType.values()) {
            when(downloads.byReleaseType(releaseType)).thenAnswer(invocation -> all.stream()
                    .filter(download -> download.type().releaseType() == releaseType)
                    .toList());
        }
    }

    /**
     * Adds a download type holding the given versions, newest first, each published a day after the
     * one before it counting back from the first of February.
     */
    Download download(int typeId, String typeName, String description, ReleaseType releaseType, String... versions) {
        DownloadType type = mock(DownloadType.class);
        when(type.id()).thenReturn(typeId);
        when(type.name()).thenReturn(typeName);
        when(type.description()).thenReturn(description);
        when(type.releaseType()).thenReturn(releaseType);

        List<AssetXO> assets = new ArrayList<>();
        for (int i = 0; i < versions.length; i++) {
            assets.add(asset(
                    versions[i],
                    OffsetDateTime.of(2026, 2, 1, 12, 0, 0, 0, ZoneOffset.UTC).minusDays(i)));
        }

        Download download = mock(Download.class);
        when(download.product()).thenReturn(product);
        when(download.type()).thenReturn(type);
        when(download.latestAssets()).thenReturn(assets);
        all.add(download);
        return download;
    }

    static AssetXO asset(String version, OffsetDateTime published) {
        MavenMeta meta = mock(MavenMeta.class);
        when(meta.version()).thenReturn(version);
        when(meta.extension()).thenReturn("jar");
        AssetXO asset = mock(AssetXO.class);
        when(asset.maven2()).thenReturn(meta);
        when(asset.lastModified()).thenReturn(published);
        return asset;
    }

    static AssetXO asset(String version) {
        return asset(version, OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }
}
