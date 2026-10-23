/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.purchase.repository;

import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.web.api.v1.kofi.payloads.KofiPost;
import de.chojo.lyna.web.api.v1.kofi.payloads.ShopItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.OFFSET_DATE_TIME;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

public class KoFiProductRepository {
    private final ProductLookup products;

    public KoFiProductRepository(ProductLookup products) {
        this.products = products;
    }

    /**
     * Links a Ko-fi code to a product, or moves it to that product from another one of the same
     * guild.
     *
     * <p>Codes are unique across the instance, so a code another guild already links is refused
     * rather than moved. Moving it would hand that guild's sales to whoever typed its code.
     *
     * @return whether the code now points at the product
     */
    public boolean create(Product product, String linkCode) {
        return query("""
                INSERT INTO kofi_products(link_code, product_id) VALUES (?,?)
                ON CONFLICT(link_code) DO UPDATE SET product_id = excluded.product_id
                WHERE (SELECT guild_id FROM product WHERE id = kofi_products.product_id)
                    = (SELECT guild_id FROM product WHERE id = excluded.product_id);
                """)
                .single(call().bind(linkCode).bind(product.id()))
                .insert()
                .changed();
    }

    public List<Mapping> listForGuild(long guildId) {
        return query("""
                SELECT kp.link_code, kp.product_id, p.name
                FROM kofi_products kp
                JOIN product p ON p.id = kp.product_id
                WHERE p.guild_id = ?
                ORDER BY p.name
                """)
                .single(call().bind(guildId))
                .map(row -> new Mapping(row.getString("link_code"), row.getInt("product_id"), row.getString("name")))
                .all();
    }

    public record Mapping(String linkCode, int productId, String productName) {}

    /**
     * Unlinks a Ko-fi code, but only one pointing at a product of this guild.
     *
     * @return whether there was such a mapping
     */
    public boolean remove(long guildId, String linkCode) {
        return query("""
                DELETE FROM kofi_products kp USING product p
                WHERE kp.product_id = p.id AND p.guild_id = ? AND kp.link_code = ?
                """).single(call().bind(guildId).bind(linkCode)).delete().changed();
    }

    public Optional<Product> byCode(String name) {
        var id = query("""
                SELECT p.id
                FROM kofi_products kp
                         LEFT JOIN product p ON kp.product_id = p.id
                WHERE ? = link_code
                """).single(call().bind(name)).map(row -> row.getInt(1)).first();

        return id.flatMap(products::byId);
    }

    public void logTransaction(KofiPost post, String raw, @Nullable ShopItem item) {
        query("""
                INSERT INTO kofi_sales(transaction_id, transaction_type, purchase_time, amount, currency, from_name, email, link_code, raw)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)""")
                .single(call().bind(post.kofiTransactionId(), UUID_STRING)
                        .bind(post.type())
                        .bind(post.timestamp(), OFFSET_DATE_TIME)
                        .bind(post.amount())
                        .bind(post.currency())
                        .bind(post.from())
                        .bind(post.email())
                        .bind(item == null ? null : item.directLinkCode())
                        .bind(raw))
                .insert();
    }
}
