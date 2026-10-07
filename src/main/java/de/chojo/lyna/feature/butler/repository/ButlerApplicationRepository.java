/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.butler.repository;

import com.google.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Which product an UpdateButler application id stands for.
 *
 * <p>Plugins already deployed ask for updates by the id UpdateButler gave them, and will never be
 * rebuilt to ask by anything else. An id names at most one product and a product carries at most one.
 */
@Singleton
public class ButlerApplicationRepository {

    /**
     * @return the product the plugin asking by this id is
     */
    public Optional<Integer> productFor(int butlerId) {
        return query("SELECT product_id FROM butler_application WHERE butler_id = ?")
                .single(call().bind(butlerId))
                .map(row -> row.getInt("product_id"))
                .first();
    }

    /**
     * @return the id each product carries, by product id, for the admin list to show
     */
    public Map<Integer, Integer> butlerIdsByProduct() {
        return query("SELECT product_id, butler_id FROM butler_application")
                .single()
                .map(row -> Map.entry(row.getInt("product_id"), row.getInt("butler_id")))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * @return whether the id already stands for a product other than this one
     */
    public boolean takenByAnother(int butlerId, int productId) {
        return productFor(butlerId).filter(owner -> owner != productId).isPresent();
    }

    /**
     * Gives a product the id, replacing any it carried. Fails on an id another product carries; ask
     * {@link #takenByAnother(int, int)} first.
     */
    public void assign(int productId, int butlerId) {
        query("""
                INSERT INTO butler_application (product_id, butler_id) VALUES (?, ?)
                ON CONFLICT (product_id) DO UPDATE SET butler_id = excluded.butler_id
                """).single(call().bind(productId).bind(butlerId)).insert();
    }

    public void clear(int productId) {
        query("DELETE FROM butler_application WHERE product_id = ?")
                .single(call().bind(productId))
                .delete();
    }
}
