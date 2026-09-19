/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.service;

import de.chojo.lyna.feature.download.entity.ReleaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a product has been released as, read by anybody.
 */
class ProductVersionServiceTest {
    private final ProductVersionService service = new ProductVersionService();
    private ProductFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProductFixture("Schematic Brush Reborn");
        fixture.download(3, "Spigot Snapshot", null, ReleaseType.SNAPSHOT, "1.8.0-SNAPSHOT");
        fixture.download(1, "Paper Stable", "For Paper servers", ReleaseType.STABLE, "1.7.8", "1.7.7");
        fixture.download(2, "Spigot Stable", "For Spigot servers", ReleaseType.STABLE, "1.7.8");
    }

    @Test
    @DisplayName("Every release type with builds is listed, stable first, each once")
    void releaseTypesStableFirst() {
        var types = service.releaseTypes(fixture.product, Set.of());

        assertEquals(
                List.of("STABLE", "SNAPSHOT"),
                types.stream().map(ProductVersionService.ReleaseTypeView::id).toList());
    }

    @Test
    @DisplayName("Which release types the reader may download is said, not hidden")
    void downloadableIsSaid() {
        var types = service.releaseTypes(fixture.product, Set.of("STABLE"));

        assertTrue(types.get(0).downloadable());
        assertFalse(types.get(1).downloadable());
    }

    @Test
    @DisplayName("A release type is described by what its download types say, and by nothing when they say nothing")
    void descriptions() {
        var types = service.releaseTypes(fixture.product, Set.of());

        assertEquals("For Paper servers · For Spigot servers", types.get(0).description());
        assertNull(types.get(1).description());
    }

    @Test
    @DisplayName("A version several download types offer is listed once, naming all of them, newest first")
    void versionsMergedNewestFirst() {
        var versions = service.versions(fixture.product, ReleaseType.STABLE, 25);

        assertEquals(
                List.of("1.7.8", "1.7.7"),
                versions.stream()
                        .map(ProductVersionService.VersionView::version)
                        .toList());
        assertEquals(List.of(1, 2), versions.get(0).downloadTypeIds());
        assertEquals(List.of(1), versions.get(1).downloadTypeIds());
    }

    @Test
    @DisplayName("The list stops at the limit asked for")
    void versionsLimited() {
        assertEquals(1, service.versions(fixture.product, ReleaseType.STABLE, 1).size());
    }
}
