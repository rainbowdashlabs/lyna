/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.service;

import de.chojo.lyna.feature.download.service.NexusAssetCache;

/**
 * The Maven artifact whose versions carry manifests. A download's classifier names which jar it
 * offers, not which manifest, so it is not part of this: every jar of a version shares one manifest.
 */
public record ManifestArtifact(String repository, String groupId, String artifactId) {
    public static ManifestArtifact of(NexusAssetCache.Coordinates coordinates) {
        return new ManifestArtifact(coordinates.repository(), coordinates.groupId(), coordinates.artifactId());
    }
}
