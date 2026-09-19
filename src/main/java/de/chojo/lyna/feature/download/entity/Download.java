/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.entity;

import de.chojo.lyna.feature.download.repository.DownloadRepository;
import de.chojo.lyna.feature.download.service.NexusAssetCache;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.nexus.entities.AssetXO;
import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.Call;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class Download implements Comparable<Download> {
    private static final DownloadRepository REPOSITORY = new DownloadRepository();

    private final Product product;
    private final int id;
    private final int typeId;
    private String repository;
    private String groupId;
    private String artifactId;

    @Nullable
    private String classifier;

    public Download(
            Product product,
            int id,
            int typeId,
            String repository,
            String groupId,
            String artifactId,
            @Nullable String classifier) {
        this.product = product;
        this.id = id;
        this.typeId = typeId;
        this.repository = repository;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classifier = classifier;
    }

    public static Download build(Product product, Row row) throws SQLException {
        return new Download(
                product,
                row.getInt("id"),
                row.getInt("type_id"),
                row.getString("repository"),
                row.getString("group_id"),
                row.getString("artifact_id"),
                row.getString("classifier"));
    }

    public int id() {
        return id;
    }

    public Product product() {
        return product;
    }

    public DownloadType type() {
        return product.products().licenseGuild().downloadTypes().byId(typeId).orElse(null);
    }

    public String repository() {
        return repository;
    }

    public void repository(String repository) {
        if (set("repository", stmt -> stmt.bind(repository))) {
            this.repository = repository;
        }
    }

    public String groupId() {
        return groupId;
    }

    public void groupId(String groupId) {
        if (set("group_id", stmt -> stmt.bind(groupId))) {
            this.groupId = groupId;
        }
    }

    public String artifactId() {
        return artifactId;
    }

    public void artifactId(String artifactId) {
        if (set("artifact_id", stmt -> stmt.bind(artifactId))) {
            this.artifactId = artifactId;
        }
    }

    public String classifier() {
        return classifier;
    }

    public void classifier(String classifier) {
        if (set("classifier", stmt -> stmt.bind(classifier))) {
            this.classifier = classifier;
        }
    }

    private boolean set(String column, Function<Call, Call> consumer) {
        return REPOSITORY.set(product.id(), typeId, column, consumer);
    }

    public boolean delete() {
        return REPOSITORY.delete(product.id(), typeId);
    }

    public List<AssetXO> latestAssets() {
        return assets().latest(coordinates());
    }

    public Optional<AssetXO> assetByVersion(String version) {
        return assets().byVersion(coordinates(), version);
    }

    /**
     * Where this download's jars live in Nexus.
     */
    public NexusAssetCache.Coordinates coordinates() {
        return new NexusAssetCache.Coordinates(repository, groupId, artifactId, classifier);
    }

    private NexusAssetCache assets() {
        return product.products().licenseGuild().guilds().assets();
    }

    public void downloaded(String version) {
        REPOSITORY.recordDownload(id, version);
    }

    @Override
    public int compareTo(@NotNull Download o) {
        int compare = Integer.compare(
                type().releaseType().ordinal(), o.type().releaseType().ordinal());
        if (compare != 0) return compare;

        return String.CASE_INSENSITIVE_ORDER.compare(type().name(), o.type().name());
    }
}
