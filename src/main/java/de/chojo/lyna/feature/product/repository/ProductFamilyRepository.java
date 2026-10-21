/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.product.repository;

import com.google.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Which product is an add-on of which.
 */
@Singleton
public class ProductFamilyRepository {
    /**
     * Where a product stands in its family.
     *
     * @param guildId  the guild it belongs to, which its parent has to share
     * @param parentId the product it is an add-on of, or null
     * @param addons   how many products are add-ons of it
     */
    public record Standing(long guildId, Integer parentId, int addons) {}

    /**
     * @return where the product stands, or nothing for a product that does not exist
     */
    public Optional<Standing> standing(int productId) {
        return query("""
                SELECT p.guild_id, p.parent_id, (SELECT count(*) FROM product c WHERE c.parent_id = p.id) AS addons
                FROM product p
                WHERE p.id = ?
                """)
                .single(call().bind(productId))
                .map(row -> new Standing(
                        row.getLong("guild_id"), (Integer) row.getObject("parent_id"), row.getInt("addons")))
                .first();
    }

    /**
     * Makes a product an add-on of another, or a product of its own again when the parent is null.
     */
    public void parent(int productId, Integer parentId) {
        query("UPDATE product SET parent_id = ? WHERE id = ?")
                .single(call().bind(parentId).bind(productId))
                .update();
    }

    /**
     * @return every add-on's parent, by the add-on's id
     */
    public Map<Integer, Integer> parents() {
        return query("SELECT id, parent_id FROM product WHERE parent_id IS NOT NULL")
                .single()
                .map(row -> Map.entry(row.getInt("id"), row.getInt("parent_id")))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
