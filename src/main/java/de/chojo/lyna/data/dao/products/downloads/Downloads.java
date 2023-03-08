package de.chojo.lyna.data.dao.products.downloads;

import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.products.Product;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Downloads {
    Product product;

    public Downloads(Product product) {
        this.product = product;
    }

    public List<Download> downloads() {
        return builder(Download.class)
                .query("SELECT type_id, group_id, artifact_id, classifier, repository FROM download WHERE product_id =?;")
                .parameter(stmt -> stmt.setInt(product.id()))
                .readRow(row -> Download.build(product, row))
                .allSync();
    }

    public Optional<Download> create(DownloadType type, String repository, String groupId, String artifactId, @Nullable String classifier) {
        return builder(Download.class)
                .query("""
                        INSERT
                        INTO
                        	download(product_id, type_id, repository, group_id, artifact_id, classifier)
                        VALUES
                        	(?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        RETURNING type_id, repository, group_id, artifact_id, classifier""")
                .parameter(stmt -> stmt.setInt(product.id()).setInt(type.id()).setString(repository).setString(groupId).setString(artifactId).setString(classifier))
                .readRow(row -> Download.build(product, row))
                .firstSync();
    }

    public Optional<Download> byType(DownloadType type) {
        return builder(Download.class)
                .query("""
                        SELECT
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
                .parameter(stmt -> stmt.setInt(product.id()).setInt(type.id()))
                .readRow(row -> Download.build(product, row))
                .firstSync();
    }
}
