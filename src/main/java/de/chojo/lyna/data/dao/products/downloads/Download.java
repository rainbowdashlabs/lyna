package de.chojo.lyna.data.dao.products.downloads;

import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.nexus.entities.AssetXO;
import de.chojo.nexus.requests.v1.search.Direction;
import de.chojo.nexus.requests.v1.search.Sort;
import de.chojo.nexus.requests.v1.search.assets.SearchRequest;
import de.chojo.sadu.exceptions.ThrowingConsumer;
import de.chojo.sadu.wrapper.util.ParamBuilder;
import de.chojo.sadu.wrapper.util.Row;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Download {
    private final Product product;
    private final int id;
    private final int typeId;
    private String repository;
    private String groupId;
    private String artifactId;
    @Nullable
    private String classifier;

    public Download(Product product, int id, int typeId, String repository, String groupId, String artifactId, @Nullable String classifier) {
        this.product = product;
        this.id = id;
        this.typeId = typeId;
        this.repository = repository;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classifier = classifier;
    }

    public static Download build(Product product, Row row) throws SQLException {
        return new Download(product,
                row.getInt("id"),
                row.getInt("type_id"),
                row.getString("repository"),
                row.getString("group_id"),
                row.getString("artifact_id"),
                row.getString("classifier")
        );
    }

    public DownloadType type() {
        return product.products().licenseGuild().downloadTypes().byId(typeId).orElse(null);
    }

    public String repository() {
        return repository;
    }

    public void repository(String repository) {
        if (set("repository", stmt -> stmt.setString(repository))) {
            this.repository = repository;
        }
    }

    public String groupId() {
        return groupId;
    }

    public void groupId(String groupId) {
        if (set("group_id", stmt -> stmt.setString(groupId))) {
            this.groupId = groupId;
        }
    }

    public String artifactId() {
        return artifactId;
    }

    public void artifactId(String artifactId) {
        if (set("artifact_id", stmt -> stmt.setString(artifactId))) {
            this.artifactId = artifactId;
        }
    }

    public String classifier() {
        return classifier;
    }

    public void classifier(String classifier) {
        if (set("classifier", stmt -> stmt.setString(classifier))) {
            this.classifier = classifier;
        }
    }

    private boolean set(String column, ThrowingConsumer<ParamBuilder, SQLException> consumer) {
        return builder().query("UPDATE download SET %s = ? WHERE product_id = ? AND type_id = ?", column)
                .parameter(stmt -> {
                    consumer.accept(stmt);
                    stmt.setInt(product.id()).setInt(typeId);
                }).update()
                .sendSync()
                .changed();
    }

    public boolean delete() {
        return builder()
                .query("DELETE FROM download WHERE product_id = ? AND type_id = ?")
                .parameter(stmt -> stmt.setInt(product.id()).setInt(typeId))
                .delete()
                .sendSync()
                .changed();
    }

    public List<AssetXO> latestAssets() {
        SearchRequest jar = product.nexus().v1().search().assets().search()
                .repository(repository)
                .mavenGroupId(groupId)
                .mavenArtifactId(artifactId)
                .mavenExtension("jar")
                // We order by version
                .sort(Sort.VERSION)
                // Newest first
                .direction(Direction.DESC);
        if (classifier != null) {
            jar.mavenClassifier(classifier);
        }
        return jar.complete()
                .items()
                .stream()
                // We can not filter for null classifiers, so we do it afterwards
                .filter(e -> classifier != null || e.maven2().classifier() == null)
                .toList();
    }

    public void downloaded(String version) {
        builder().query("""
                        INSERT
                        INTO download_stat AS d
                        	(download_id, version, count)
                        VALUES
                        	(?, ?, 1)
                        ON CONFLICT (download_id, date, version) DO UPDATE SET
                        	count = d.count + 1""")
                .parameter(stmt -> stmt.setInt(id).setString(version))
                .insert()
                .sendSync();
    }
}
