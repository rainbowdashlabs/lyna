package de.chojo.lyna.data.dao.products.downloads;

import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Role;
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
                .query("SELECT id, type_id, group_id, artifact_id, classifier, repository FROM download WHERE product_id =?;")
                .parameter(stmt -> stmt.setInt(product.id()))
                .readRow(row -> Download.build(product, row))
                .allSync();
    }

    public Optional<Download> byReleaseTypeAndArtifact(ReleaseType releaseType, @Nullable String artifact) {
        return builder(Download.class)
                .query("""
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
                .parameter(stmt -> stmt.setInt(product.id()).setString(artifact).setString(artifact).setEnum(releaseType))
                .readRow(row -> Download.build(product, row))
                .firstSync();
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
                        RETURNING id, type_id, repository, group_id, artifact_id, classifier""")
                .parameter(stmt -> stmt.setInt(product.id()).setInt(type.id()).setString(repository).setString(groupId).setString(artifactId).setString(classifier))
                .readRow(row -> Download.build(product, row))
                .firstSync();
    }

    public Optional<Download> byType(DownloadType type) {
        return builder(Download.class)
                .query("""
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
                .parameter(stmt -> stmt.setInt(product.id()).setInt(type.id()))
                .readRow(row -> Download.build(product, row))
                .firstSync();
    }

    public boolean grant(Role role, ReleaseType type) {
        return builder()
                .query("INSERT INTO role_access(role_id, product_id, release_type) VALUES (?,?,?::RELEASE_TYPE) ON CONFLICT DO NOTHING")
                .parameter(stmt -> stmt.setLong(role.getIdLong()).setInt(product.id()).setEnum(type))
                .insert()
                .sendSync()
                .changed();
    }

    public boolean revoke(Role role, ReleaseType type) {
        return builder()
                .query("DELETE FROM role_access WHERE role_id = ? AND product_id = ? AND release_type = ?::RELEASE_TYPE")
                .parameter(stmt -> stmt.setLong(role.getIdLong()).setInt(product.id()).setEnum(type))
                .insert()
                .sendSync()
                .changed();
    }

    public Optional<Download> byReleaseType(ReleaseType releaseType) {
        return builder(Download.class)
                .query("""
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
                .parameter(stmt -> stmt.setInt(product.id()).setEnum(releaseType))
                .readRow(row -> Download.build(product, row))
                .firstSync();
    }
}
