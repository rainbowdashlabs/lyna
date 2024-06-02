package de.chojo.lyna.data.dao.products.downloads;

import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Role;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Downloads {
    Product product;

    public Downloads(Product product) {
        this.product = product;
    }

    public List<Download> downloads() {
        return query("SELECT id, type_id, group_id, artifact_id, classifier, repository FROM download WHERE product_id =?;")
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

    public Optional<Download> create(DownloadType type, String repository, String groupId, String artifactId, @Nullable String classifier) {
        return query("""
                INSERT
                INTO
                	download(product_id, type_id, repository, group_id, artifact_id, classifier)
                VALUES
                	(?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                RETURNING id, type_id, repository, group_id, artifact_id, classifier""")
                .single(call().bind(product.id()).bind(type.id()).bind(repository).bind(groupId).bind(artifactId).bind(classifier))
                .map(row -> Download.build(product, row))
                .first();
    }

    public Optional<Download> byType(DownloadType type) {
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
                .single(call().bind(product.id()).bind(type.id()))
                .map(row -> Download.build(product, row))
                .first();
    }

    public boolean grant(Role role, ReleaseType type) {
        return query("INSERT INTO role_access(role_id, product_id, release_type) VALUES (?,?,?::RELEASE_TYPE) ON CONFLICT DO NOTHING")
                .single(call().bind(role.getIdLong()).bind(product.id()).bind(type))
                .insert()
                .changed();
    }

    public boolean revoke(Role role, ReleaseType type) {
        return query("DELETE FROM role_access WHERE role_id = ? AND product_id = ? AND release_type = ?::RELEASE_TYPE")
                .single(call().bind(role.getIdLong()).bind(product.id()).bind(type))
                .insert()
                .changed();
    }

    public Optional<Download> byReleaseType(ReleaseType releaseType) {
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
                .first();
    }
}
