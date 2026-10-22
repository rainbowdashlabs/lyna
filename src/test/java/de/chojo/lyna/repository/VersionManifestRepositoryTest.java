/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.manifest.repository.VersionManifestRepository;
import de.chojo.lyna.feature.manifest.service.ManifestArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionManifestRepositoryTest extends RepositoryTestBase {
    private static final ManifestArtifact ARTIFACT = new ManifestArtifact("releases", "de.chojo", "bloodnight");
    private static final ManifestArtifact OTHER = new ManifestArtifact("releases", "de.chojo", "other");

    private final VersionManifestRepository manifests = new VersionManifestRepository();

    @BeforeEach
    void seed() throws SQLException {
        clear("version_manifest");
    }

    @Test
    @DisplayName("Manifests are kept by coordinates and version, and replaced by a new sha1")
    void storeAndReplace() {
        assertTrue(manifests.all(ARTIFACT).isEmpty());

        manifests.store(ARTIFACT, "1.0.0", "a", "{\"schema\": 1}");
        manifests.store(OTHER, "1.0.0", "o", "{\"schema\": 1}");
        manifests.store(ARTIFACT, "1.0.0", "b", "{\"schema\": 1, \"name\": \"P\"}");

        var stored = manifests.all(ARTIFACT);
        assertEquals(1, stored.size());
        assertEquals("b", stored.get("1.0.0").sha1());
        assertTrue(stored.get("1.0.0").json().contains("\"name\": \"P\""));
        assertEquals("o", manifests.all(OTHER).get("1.0.0").sha1());
    }
}
