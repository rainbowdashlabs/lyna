/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.icon.repository;

import com.google.inject.Singleton;

import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Which products carry an icon, and what kind it is. The images are files on disk.
 */
@Singleton
public class ProductIconRepository {

    /**
     * @param mime      what the stored files are
     * @param updatedAt when it last changed, which is what a caching browser is told
     */
    public record Icon(String mime, Instant updatedAt) {}

    public void record(int productId, String mime) {
        query("""
                INSERT INTO product_icon (product_id, mime, updated_at) VALUES (?, ?, now())
                ON CONFLICT (product_id) DO UPDATE SET mime = excluded.mime, updated_at = now()
                """).single(call().bind(productId).bind(mime)).insert();
    }

    public Optional<Icon> of(int productId) {
        return query("SELECT mime, updated_at FROM product_icon WHERE product_id = ?")
                .single(call().bind(productId))
                .map(row -> new Icon(
                        row.getString("mime"), row.getTimestamp("updated_at").toInstant()))
                .first();
    }

    public boolean remove(int productId) {
        return query("DELETE FROM product_icon WHERE product_id = ?")
                .single(call().bind(productId))
                .delete()
                .changed();
    }

    /**
     * @return the ids of every product carrying an icon, for the catalogue to say which have one
     */
    public java.util.Set<Integer> withIcon() {
        return java.util.Set.copyOf(query("SELECT product_id FROM product_icon")
                .single()
                .map(row -> row.getInt("product_id"))
                .all());
    }
}
