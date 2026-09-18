/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.repository;

import com.google.inject.Singleton;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.sadu.queries.api.call.Call;

import java.util.function.Function;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Where a product's artifacts come from, and how often they were fetched.
 */
@Singleton
public class DownloadRepository {

    /**
     * Writes one column of a download.
     *
     * <p>The column is interpolated because a placeholder cannot name one. Every caller passes a
     * literal, and nothing here takes a column name from outside.
     */
    public boolean set(int productId, int typeId, String column, Function<Call, Call> value) {
        return query("UPDATE download SET %s = ? WHERE product_id = ? AND type_id = ?", column)
                .single(value.apply(call()).bind(productId).bind(typeId))
                .update()
                .changed();
    }

    public boolean delete(int productId, int typeId) {
        return query("DELETE FROM download WHERE product_id = ? AND type_id = ?")
                .single(call().bind(productId).bind(typeId))
                .delete()
                .changed();
    }

    /**
     * Counts one fetch of a version, for the day it happened.
     */
    public void recordDownload(int downloadId, String version) {
        query("""
                INSERT
                INTO download_stat AS d
                	(download_id, version, count)
                VALUES
                	(?, ?, 1)
                ON CONFLICT (download_id, date, version) DO UPDATE SET
                	count = d.count + 1""").single(call().bind(downloadId).bind(version)).insert();
    }

    /**
     * Lets a Discord role reach a release type of a product.
     */
    public boolean grantRole(long roleId, int productId, ReleaseType type) {
        return query(
                        "INSERT INTO role_access(role_id, product_id, release_type) VALUES (?,?,?::RELEASE_TYPE) ON CONFLICT DO NOTHING")
                .single(call().bind(roleId).bind(productId).bind(type))
                .insert()
                .changed();
    }

    public boolean revokeRole(long roleId, int productId, ReleaseType type) {
        return query("DELETE FROM role_access WHERE role_id = ? AND product_id = ? AND release_type = ?::RELEASE_TYPE")
                .single(call().bind(roleId).bind(productId).bind(type))
                .insert()
                .changed();
    }
}
