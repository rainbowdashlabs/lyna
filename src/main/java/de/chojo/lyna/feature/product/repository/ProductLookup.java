/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.product.repository;

import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class ProductLookup {
    private final Guilds guilds;

    public ProductLookup(Guilds guilds) {
        this.guilds = guilds;
    }

    public Optional<Product> byId(int id) {
        return query("""
                SELECT id, guild_id, name, url, role, free FROM product WHERE id = ?
                """).single(call().bind(id)).map(this::map).first();
    }

    /**
     * Finds a product by the name a plugin gives itself. Case, spaces and punctuation are ignored, so
     * {@code BloodNight} finds "Blood Night"; the oldest product wins when names collide.
     */
    public Optional<Product> byPluginName(String name) {
        return query("""
                SELECT id, guild_id, name, url, role, free FROM product
                WHERE lower(regexp_replace(name, '[^a-zA-Z0-9]', '', 'g')) = ?
                ORDER BY id LIMIT 1
                """)
                .single(call().bind(name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT)))
                .map(this::map)
                .first();
    }

    private Product map(Row row) throws SQLException {
        return guilds.guild(row.getLong("guild_id"))
                .products()
                .byId(row.getInt("id"))
                .orElse(null);
    }
}
