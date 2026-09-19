/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import de.chojo.nexus.NexusRest;
import de.chojo.nexus.entities.AssetXO;
import de.chojo.nexus.requests.v1.search.Direction;
import de.chojo.nexus.requests.v1.search.Sort;
import de.chojo.nexus.requests.v1.search.assets.SearchRequest;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * The jars Nexus holds for a download, remembered so that listing versions does not search Nexus
 * every time somebody opens a product page.
 *
 * <p>Entries are keyed by where the jars live rather than by download, so a download pointed at a
 * different artifact asks Nexus afresh without anything having to be told. An entry older than
 * {@link #REFRESH} is refreshed in the background while the old list is still served; if Nexus cannot
 * be reached the old list keeps being served until it is {@link #EXPIRE} old.
 *
 * <p>Only the lookups are cached. The jar itself is always streamed from Nexus. There is one, held by
 * {@link de.chojo.lyna.feature.guild.Guilds}.
 */
public class NexusAssetCache {
    static final Duration REFRESH = Duration.ofMinutes(5);
    static final Duration EXPIRE = Duration.ofHours(1);

    /**
     * Where a download's jars live in Nexus.
     *
     * @param classifier null for the jar without a classifier
     */
    public record Coordinates(
            String repository,
            String groupId,
            String artifactId,
            @Nullable String classifier) {}

    /** Searches Nexus for the jars at some coordinates, newest first, optionally of one version only. */
    @FunctionalInterface
    public interface Search {
        List<AssetXO> find(Coordinates coordinates, @Nullable String version);
    }

    private final Search search;
    private final LoadingCache<Coordinates, List<AssetXO>> latest;

    public NexusAssetCache(NexusRest nexus) {
        this(
                (coordinates, version) -> search(nexus, coordinates, version),
                Ticker.systemTicker(),
                ForkJoinPool.commonPool());
    }

    NexusAssetCache(Search search, Ticker ticker, Executor executor) {
        this.search = search;
        this.latest = Caffeine.newBuilder()
                .refreshAfterWrite(REFRESH)
                .expireAfterWrite(EXPIRE)
                .ticker(ticker)
                .executor(executor)
                .build(coordinates -> search.find(coordinates, null));
    }

    /**
     * @return every jar at these coordinates, newest version first
     */
    public List<AssetXO> latest(Coordinates coordinates) {
        return latest.get(coordinates);
    }

    /**
     * One version's jar. {@code latest} means the newest.
     *
     * <p>Answered from the cached list when the version is in it. A version the list does not hold,
     * such as one older than a search returns, is looked up in Nexus directly.
     */
    public Optional<AssetXO> byVersion(Coordinates coordinates, String version) {
        List<AssetXO> known = latest(coordinates);
        if ("latest".equalsIgnoreCase(version)) return known.stream().findFirst();
        return known.stream()
                .filter(asset -> version.equals(asset.maven2().version()))
                .findFirst()
                .or(() -> search.find(coordinates, version).stream().findFirst());
    }

    private static List<AssetXO> search(NexusRest nexus, Coordinates coordinates, @Nullable String version) {
        SearchRequest jar = nexus.v1()
                .search()
                .assets()
                .search()
                .repository(coordinates.repository())
                .mavenGroupId(coordinates.groupId())
                .mavenArtifactId(coordinates.artifactId())
                .mavenExtension("jar")
                .sort(Sort.VERSION)
                .direction(Direction.DESC);
        if (coordinates.classifier() != null) jar.mavenClassifier(coordinates.classifier());
        if (version != null) jar.mavenBaseVersion(version);
        return jar.complete().items().stream()
                .filter(asset ->
                        coordinates.classifier() != null || asset.maven2().classifier() == null)
                .toList();
    }
}
