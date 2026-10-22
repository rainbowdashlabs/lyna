/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.repository;

import com.google.inject.Singleton;
import de.chojo.lyna.feature.manifest.service.ManifestArtifact;

import java.util.Map;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The plugin manifests Lyna has read from Nexus, by Maven coordinates.
 */
@Singleton
public class VersionManifestRepository {
    /**
     * A manifest as it was stored.
     *
     * @param json the manifest as the build published it
     */
    public record Stored(String sha1, String json) {}

    /**
     * @return every stored manifest of the artifact, by version
     */
    public Map<String, Stored> all(ManifestArtifact artifact) {
        return query("""
                SELECT version, sha1, manifest::TEXT AS manifest
                FROM version_manifest
                WHERE repository = ? AND group_id = ? AND artifact_id = ?
                """)
                .single(call().bind(artifact.repository())
                        .bind(artifact.groupId())
                        .bind(artifact.artifactId()))
                .map(row -> Map.entry(
                        row.getString("version"), new Stored(row.getString("sha1"), row.getString("manifest"))))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Stores one version's manifest, replacing what was stored for it.
     */
    public void store(ManifestArtifact artifact, String version, String sha1, String json) {
        query("""
                INSERT INTO version_manifest (repository, group_id, artifact_id, version, sha1, manifest)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (repository, group_id, artifact_id, version)
                    DO UPDATE SET sha1 = excluded.sha1, manifest = excluded.manifest
                """)
                .single(call().bind(artifact.repository())
                        .bind(artifact.groupId())
                        .bind(artifact.artifactId())
                        .bind(version)
                        .bind(sha1)
                        .bind(json))
                .insert();
    }
}
