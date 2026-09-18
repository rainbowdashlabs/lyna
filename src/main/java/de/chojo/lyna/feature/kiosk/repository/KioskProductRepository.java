/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.kiosk.repository;

import de.chojo.lyna.feature.kiosk.entity.KioskProduct;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The public catalogue: every product of every guild, merged.
 *
 * <p>Guild ownership is an administrative concern, not a shopping one, so a visitor browsing the
 * storefront never picks a guild. Nothing here touches the gateway, which is what lets the page
 * answer to somebody who is not signed in.
 */
public class KioskProductRepository {
    /** Ko-fi serves a shop item at this address followed by its direct link code. */
    private static final String KOFI_SHOP_URL = "https://ko-fi.com/s/";

    /**
     * A product may carry more than one Ko-fi code, so the join takes the first by code rather than
     * a row per mapping - otherwise the same product reaches the storefront as several tiles.
     *
     * @return every product, free and premium alike, by name
     */
    public List<KioskProduct> all() {
        return query("""
                SELECT p.id, p.guild_id, p.name, p.url, p.icon_url, p.free, p.description, kp.link_code
                FROM product p
                LEFT JOIN LATERAL (
                    SELECT link_code FROM kofi_products WHERE product_id = p.id ORDER BY link_code LIMIT 1
                ) kp ON TRUE
                ORDER BY p.name
                """)
                .single()
                .map(row -> new KioskProduct(
                        row.getInt("id"),
                        row.getLong("guild_id"),
                        row.getString("name"),
                        row.getString("url"),
                        row.getString("icon_url"),
                        row.getBoolean("free"),
                        row.getString("link_code") == null ? null : KOFI_SHOP_URL + row.getString("link_code"),
                        row.getString("description")))
                .all();
    }

    /**
     * One product, for its own page.
     *
     * <p>Read the same way {@link #all} reads the catalogue, so the page answers to a visitor who is
     * not signed in and to an instance whose bot is not connected.
     *
     * @param productId the product
     * @return the product, or nothing when no such product exists
     */
    public java.util.Optional<KioskProduct> byId(int productId) {
        return query("""
                SELECT p.id, p.guild_id, p.name, p.url, p.icon_url, p.free, p.description, kp.link_code
                FROM product p
                LEFT JOIN LATERAL (
                    SELECT link_code FROM kofi_products WHERE product_id = p.id ORDER BY link_code LIMIT 1
                ) kp ON TRUE
                WHERE p.id = ?
                """)
                .single(call().bind(productId))
                .map(row -> new KioskProduct(
                        row.getInt("id"),
                        row.getLong("guild_id"),
                        row.getString("name"),
                        row.getString("url"),
                        row.getString("icon_url"),
                        row.getBoolean("free"),
                        row.getString("link_code") == null ? null : KOFI_SHOP_URL + row.getString("link_code"),
                        row.getString("description")))
                .first();
    }

    /**
     * Writes what a product says about itself.
     */
    public void description(int productId, String description) {
        query("UPDATE product SET description = ? WHERE id = ?")
                .single(call().bind(description).bind(productId))
                .update();
    }

    /**
     * Whether a product is free to everybody.
     *
     * <p>Read from the table rather than from a resolved product, so that whether a caller may have
     * something is decided before anything needs the gateway. An unauthorised request is then
     * refused as such, instead of failing on a bot that is not there.
     *
     * @param productId the product
     * @return whether it is free, and false for a product that does not exist
     */
    public boolean isFree(int productId) {
        return query("SELECT free FROM product WHERE id = ?")
                .single(call().bind(productId))
                .map(row -> row.getBoolean("free"))
                .first()
                .orElse(false);
    }

    /**
     * Records where a product's icon is hosted. A blank address takes the icon away again, which is
     * what leaves the tile on its generated monogram.
     */
    public void iconUrl(int productId, String iconUrl) {
        query("UPDATE product SET icon_url = ? WHERE id = ?")
                .single(call().bind(iconUrl == null || iconUrl.isBlank() ? null : iconUrl.trim())
                        .bind(productId))
                .update();
    }
}
