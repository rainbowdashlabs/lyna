package de.chojo.lyna.data.dao.products.downloads;

import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.sadu.exceptions.ThrowingConsumer;
import de.chojo.sadu.wrapper.util.ParamBuilder;
import de.chojo.sadu.wrapper.util.Row;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Download {
    private final Product product;
    private final int typeId;
    private String repository;
    private String groupId;
    private String artifactId;
    @Nullable
    private String classifier;

    public Download(Product product, int typeId, String repository, String groupId, String artifactId, @Nullable String classifier) {
        this.product = product;
        this.typeId = typeId;
        this.repository = repository;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classifier = classifier;
    }

    public static Download build(Product product, Row row) throws SQLException {
        return new Download(product,
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
}
