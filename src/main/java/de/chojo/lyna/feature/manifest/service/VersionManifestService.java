/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.download.service.NexusAssetCache;
import de.chojo.lyna.feature.manifest.entity.PluginManifest;
import de.chojo.lyna.feature.manifest.repository.VersionManifestRepository;
import de.chojo.nexus.NexusRest;
import de.chojo.nexus.requests.v1.search.Direction;
import de.chojo.nexus.requests.v1.search.Sort;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The plugin manifests published beside a product's builds, with the classifier {@code manifest}.
 *
 * <p>One search lists every version's manifest with its sha1, and only a manifest whose sha1 Lyna has
 * not stored is downloaded. A release never changes, so it is downloaded once; a snapshot published
 * again under the same version has a new sha1 and is read again. The search is remembered for as long
 * as the version listings are, so pages ask Nexus no more often than before. When Nexus cannot be
 * asked, what is stored is served.
 *
 * <p>A build without a manifest has none here. Builds published before manifests existed keep working
 * as they did.
 */
@Singleton
public class VersionManifestService {
    private static final Logger log = getLogger(VersionManifestService.class);

    /** A manifest as Nexus lists it, read only when its sha1 is new. */
    public record Published(String version, String sha1, Supplier<String> content) {}

    /** Lists the manifests Nexus holds for an artifact. */
    @FunctionalInterface
    public interface Source {
        List<Published> list(ManifestArtifact artifact);
    }

    private final VersionManifestRepository repository;
    private final Source source;
    private final LoadingCache<ManifestArtifact, Map<String, PluginManifest>> manifests;

    @Inject
    public VersionManifestService(VersionManifestRepository repository, NexusRest nexus) {
        this(repository, artifact -> search(nexus, artifact), Ticker.systemTicker(), ForkJoinPool.commonPool());
    }

    VersionManifestService(VersionManifestRepository repository, Source source, Ticker ticker, Executor executor) {
        this.repository = repository;
        this.source = source;
        this.manifests = Caffeine.newBuilder()
                .refreshAfterWrite(Duration.ofMinutes(5))
                .expireAfterWrite(Duration.ofHours(1))
                .ticker(ticker)
                .executor(executor)
                .build(this::load);
    }

    /**
     * @return the manifests of every version at these coordinates that has one, by version
     */
    public Map<String, PluginManifest> manifests(NexusAssetCache.Coordinates coordinates) {
        return manifests.get(ManifestArtifact.of(coordinates));
    }

    /**
     * @return the manifest of one version, or nothing when it was published without one
     */
    public Optional<PluginManifest> manifest(NexusAssetCache.Coordinates coordinates, String version) {
        return Optional.ofNullable(manifests(coordinates).get(version));
    }

    private Map<String, PluginManifest> load(ManifestArtifact artifact) {
        try {
            refresh(artifact);
        } catch (RuntimeException e) {
            log.debug("Could not list the manifests of {}; serving what is stored", artifact, e);
        }
        Map<String, PluginManifest> read = new LinkedHashMap<>();
        repository.all(artifact).forEach((version, stored) -> ManifestReader.read(stored.json())
                .ifPresent(manifest -> read.put(version, manifest)));
        return Map.copyOf(read);
    }

    private void refresh(ManifestArtifact artifact) {
        Map<String, VersionManifestRepository.Stored> stored = repository.all(artifact);
        for (Published published : source.list(artifact)) {
            VersionManifestRepository.Stored known = stored.get(published.version());
            if (known != null && known.sha1().equals(published.sha1())) continue;
            try {
                String json = published.content().get();
                if (ManifestReader.read(json).isEmpty()) {
                    log.debug("The manifest of {} {} is not one Lyna reads", artifact, published.version());
                    continue;
                }
                repository.store(artifact, published.version(), published.sha1(), json);
            } catch (RuntimeException e) {
                log.debug("Could not read the manifest of {} {}", artifact, published.version(), e);
            }
        }
    }

    private static List<Published> search(NexusRest nexus, ManifestArtifact artifact) {
        return nexus
                .v1()
                .search()
                .assets()
                .search()
                .repository(artifact.repository())
                .mavenGroupId(artifact.groupId())
                .mavenArtifactId(artifact.artifactId())
                .mavenExtension("json")
                .mavenClassifier("manifest")
                .sort(Sort.VERSION)
                .direction(Direction.DESC)
                .complete()
                .items()
                .stream()
                .map(asset -> new Published(
                        asset.maven2().version(),
                        asset.checksum().sha1(),
                        () -> new String(asset.download().complete(), StandardCharsets.UTF_8)))
                .toList();
    }
}
