/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.service;

import de.chojo.lyna.feature.download.service.NexusAssetCache;
import de.chojo.lyna.feature.manifest.repository.VersionManifestRepository;
import de.chojo.lyna.repository.RepositoryTestBase;
import de.chojo.nexus.NexusRest;
import de.chojo.nexus.entities.AssetXO;
import de.chojo.nexus.entities.PageAssetXO;
import de.chojo.nexus.requests.v1.search.assets.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Manifests read lazily from Nexus: once per sha1, and from what is stored when Nexus cannot answer.
 */
class VersionManifestServiceTest extends RepositoryTestBase {
    private static final NexusAssetCache.Coordinates JAR =
            new NexusAssetCache.Coordinates("releases", "de.chojo", "bloodnight", null);
    private static final NexusAssetCache.Coordinates SHADED =
            new NexusAssetCache.Coordinates("releases", "de.chojo", "bloodnight", "all");

    private final VersionManifestRepository repository = new VersionManifestRepository();
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicInteger listed = new AtomicInteger();
    private final AtomicInteger fetched = new AtomicInteger();
    private final List<VersionManifestService.Published> published = new ArrayList<>();
    private VersionManifestService service;

    @BeforeEach
    void setUp() throws SQLException {
        clear("version_manifest");
        service = new VersionManifestService(
                repository,
                artifact -> {
                    listed.incrementAndGet();
                    return List.copyOf(published);
                },
                nanos::get,
                Runnable::run);
    }

    private void publish(String version, String sha1, String min) {
        published.removeIf(entry -> entry.version().equals(version));
        published.add(new VersionManifestService.Published(version, sha1, () -> {
            fetched.incrementAndGet();
            return "{\"schema\": 1, \"version\": \"%s\", \"minecraft\": {\"min\": \"%s\"}}".formatted(version, min);
        }));
    }

    private void later(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    @DisplayName("Each manifest is downloaded once, and every jar of a version shares it")
    void readOnce() {
        publish("1.0.0", "a", "1.20");
        publish("1.1.0", "b", "1.21");

        assertEquals("1.20", service.manifest(JAR, "1.0.0").orElseThrow().minecraftMin());
        assertEquals("1.21", service.manifest(SHADED, "1.1.0").orElseThrow().minecraftMin());
        assertTrue(service.manifest(JAR, "0.9.0").isEmpty(), "a build without a manifest has none");
        assertEquals(1, listed.get(), "one search answers every version and every classifier");

        later(Duration.ofMinutes(6));
        service.manifests(JAR);
        assertEquals(2, listed.get(), "the search is asked again after five minutes");
        assertEquals(2, fetched.get(), "a sha1 already stored is not downloaded again");
    }

    @Test
    @DisplayName("A snapshot published again under its version is read again")
    void redeployed() {
        publish("2.0.0-SNAPSHOT", "a", "1.20");
        assertEquals(
                "1.20", service.manifest(JAR, "2.0.0-SNAPSHOT").orElseThrow().minecraftMin());

        publish("2.0.0-SNAPSHOT", "b", "1.21");
        later(Duration.ofMinutes(6));
        service.manifests(JAR);

        assertEquals(
                "1.21", service.manifest(JAR, "2.0.0-SNAPSHOT").orElseThrow().minecraftMin());
        assertEquals(
                "b",
                repository.all(ManifestArtifact.of(JAR)).get("2.0.0-SNAPSHOT").sha1());
    }

    @Test
    @DisplayName("A manifest Lyna cannot read is not stored, and a failed download does not stop the rest")
    void unreadable() {
        published.add(new VersionManifestService.Published("1.0.0", "x", () -> "{\"schema\": 9}"));
        published.add(new VersionManifestService.Published("1.1.0", "y", () -> {
            throw new IllegalStateException("gone");
        }));
        publish("1.2.0", "z", "1.21");

        assertEquals(List.of("1.2.0"), List.copyOf(service.manifests(JAR).keySet()));
        assertEquals(1, repository.all(ManifestArtifact.of(JAR)).size());
    }

    @Test
    @DisplayName("Nexus is asked for the json files with the manifest classifier, and each is downloaded by its asset")
    void searchesNexus() {
        NexusRest nexus = mock(NexusRest.class, Answers.RETURNS_DEEP_STUBS);
        SearchRequest search = mock(SearchRequest.class, Answers.RETURNS_SELF);
        PageAssetXO page = mock(PageAssetXO.class);
        AssetXO asset = mock(AssetXO.class, Answers.RETURNS_DEEP_STUBS);
        when(asset.maven2().version()).thenReturn("1.0.0");
        when(asset.checksum().sha1()).thenReturn("abc");
        when(asset.download().complete())
                .thenReturn("{\"schema\": 1, \"minecraft\": {\"min\": \"1.20\"}}".getBytes(StandardCharsets.UTF_8));
        when(page.items()).thenReturn(List.of(asset));
        when(search.complete()).thenReturn(page);
        when(nexus.v1().search().assets().search()).thenReturn(search);

        var manifest = new VersionManifestService(repository, nexus).manifest(SHADED, "1.0.0");

        assertEquals("1.20", manifest.orElseThrow().minecraftMin());
        verify(search).repository("releases");
        verify(search).mavenGroupId("de.chojo");
        verify(search).mavenArtifactId("bloodnight");
        verify(search).mavenExtension("json");
        verify(search).mavenClassifier("manifest");
        assertEquals(
                "abc", repository.all(ManifestArtifact.of(JAR)).get("1.0.0").sha1());
    }

    @Test
    @DisplayName("When Nexus cannot be asked, what is stored is served")
    void nexusDown() {
        publish("1.0.0", "a", "1.20");
        service.manifests(JAR);

        VersionManifestService fresh = new VersionManifestService(
                repository,
                artifact -> {
                    throw new IllegalStateException("Nexus is down");
                },
                nanos::get,
                Runnable::run);

        assertEquals("1.20", fresh.manifest(JAR, "1.0.0").orElseThrow().minecraftMin());
    }
}
