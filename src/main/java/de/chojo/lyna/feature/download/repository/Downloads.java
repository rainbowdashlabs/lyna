/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.repository;

import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.DownloadType;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.product.entity.Product;
import net.dv8tion.jda.api.entities.Role;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Downloads {
    private static final DownloadRepository REPOSITORY = new DownloadRepository();

    Product product;

    public Downloads(Product product) {
        this.product = product;
    }

    public List<Download> downloads() {
        return query(
                        "SELECT id, type_id, group_id, artifact_id, classifier, repository FROM download WHERE product_id =?;")
                .single(call().bind(product.id()))
                .map(row -> Download.build(product, row))
                .all();
    }

    public Optional<Download> byReleaseTypeAndArtifact(ReleaseType releaseType, @Nullable String artifact) {
        return query("""
                SELECT
                    d.id,
                	type_id,
                	group_id,
                	artifact_id,
                	classifier,
                	repository
                FROM
                	download d
                		LEFT JOIN download_type t
                		ON d.type_id = t.id
                WHERE product_id = ?
                  AND (artifact_id = ? OR ? IS NULL)
                  AND release_type = ?::RELEASE_TYPE;""")
                .single(call().bind(product.id()).bind(artifact).bind(artifact).bind(releaseType))
                .map(row -> Download.build(product, row))
                .first();
    }

    public Optional<Download> create(
            DownloadType type, String repository, String groupId, String artifactId, @Nullable String classifier) {
        return query("""
                INSERT
                INTO
                	download(product_id, type_id, repository, group_id, artifact_id, classifier)
                VALUES
                	(?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                RETURNING id, type_id, repository, group_id, artifact_id, classifier""")
                .single(call().bind(product.id())
                        .bind(type.id())
                        .bind(repository)
                        .bind(groupId)
                        .bind(artifactId)
                        .bind(classifier))
                .map(row -> Download.build(product, row))
                .first();
    }

    public Optional<Download> byType(DownloadType type) {
        return byType(type.id());
    }

    public Optional<Download> byType(int type) {
        return query("""
                SELECT
                    id,
                	product_id,
                	type_id,
                	group_id,
                	artifact_id,
                	classifier,
                	repository
                FROM
                	download
                WHERE product_id = ?
                  AND type_id = ?""")
                .single(call().bind(product.id()).bind(type))
                .map(row -> Download.build(product, row))
                .first();
    }

    public boolean grant(Role role, ReleaseType type) {
        return REPOSITORY.grantRole(role.getIdLong(), product.id(), type);
    }

    public boolean revoke(Role role, ReleaseType type) {
        return REPOSITORY.revokeRole(role.getIdLong(), product.id(), type);
    }

    public List<Download> byReleaseType(ReleaseType releaseType) {
        return query("""
                SELECT
                    d.id,
                	type_id,
                	group_id,
                	artifact_id,
                	classifier,
                	repository
                FROM
                	download d
                		LEFT JOIN download_type t
                		ON d.type_id = t.id
                WHERE product_id = ?
                  AND release_type = ?::RELEASE_TYPE;""")
                .single(call().bind(product.id()).bind(releaseType))
                .map(row -> Download.build(product, row))
                .all();
    }
}
