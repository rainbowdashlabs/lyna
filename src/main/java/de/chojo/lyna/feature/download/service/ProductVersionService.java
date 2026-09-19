/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.service;

import com.google.inject.Singleton;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.nexus.entities.AssetXO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What a product has been released as: its release types and the versions in each.
 *
 * <p>Anybody may read this, whether or not they may download. Somebody deciding whether to buy a
 * plugin learns from it that the plugin is maintained, and nothing here is worth keeping from them.
 * Which of it the reader may download is said alongside, not by leaving the rest out.
 */
@Singleton
public class ProductVersionService {

    /**
     * @param id           the release type, as {@link ReleaseType} names it
     * @param description  what the download types in it say about themselves, or null
     * @param downloadable whether the reader may download builds of this release type
     */
    public record ReleaseTypeView(String id, String description, boolean downloadable) {}

    /**
     * @param downloadTypeIds the download types this version is offered in
     */
    public record VersionView(String version, Instant publishedAt, List<Integer> downloadTypeIds) {}

    /**
     * The release types the product has builds of, stable first.
     *
     * @param downloadable the release types the reader may download
     */
    public List<ReleaseTypeView> releaseTypes(Product product, Set<String> downloadable) {
        return product.downloads().downloads().stream()
                .map(download -> download.type().releaseType())
                .distinct()
                .sorted(Comparator.comparing(Enum::ordinal))
                .map(type ->
                        new ReleaseTypeView(type.name(), describe(product, type), downloadable.contains(type.name())))
                .toList();
    }

    /**
     * The versions of one release type, newest first, each once however many download types offer it.
     */
    public List<VersionView> versions(Product product, ReleaseType releaseType, int limit) {
        Map<String, VersionView> byVersion = new LinkedHashMap<>();
        for (Download download : product.downloads().byReleaseType(releaseType)) {
            for (AssetXO asset : download.latestAssets()) {
                VersionView existing = byVersion.computeIfAbsent(
                        asset.maven2().version(),
                        version -> new VersionView(version, asset.lastModified().toInstant(), new ArrayList<>()));
                if (!existing.downloadTypeIds().contains(download.type().id())) {
                    existing.downloadTypeIds().add(download.type().id());
                }
            }
        }
        return byVersion.values().stream()
                .sorted(Comparator.comparing(VersionView::publishedAt).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * What the download types of a release type say about themselves, joined. Nothing when nobody
     * wrote anything.
     */
    private static String describe(Product product, ReleaseType releaseType) {
        return product.downloads().byReleaseType(releaseType).stream()
                .map(download -> download.type().description())
                .filter(description -> description != null && !description.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(TreeSet::new),
                        descriptions -> descriptions.isEmpty() ? null : String.join(" · ", descriptions)));
    }
}
