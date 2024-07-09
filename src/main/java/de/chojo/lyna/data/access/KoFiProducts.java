package de.chojo.lyna.data.access;

import de.chojo.lyna.web.api.v1.kofi.payloads.KofiPost;
import de.chojo.lyna.web.api.v1.kofi.payloads.ShopItem;
import de.chojo.lyna.data.dao.products.Product;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.OFFSET_DATE_TIME;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

public class KoFiProducts {
    private final Products products;

    public KoFiProducts(Products products) {
        this.products = products;
    }

    public void create(Product product, String linkCode) {
        query("""
                INSERT INTO kofi_products(link_code, product_id) VALUES (?,?)
                ON CONFLICT(link_code) DO UPDATE SET product_id = excluded.product_id;
                """)
                .single(call().bind(linkCode).bind(product.id()))
                .insert();
    }

    public Optional<Product> byCode(String name) {
        var id = query("""
                SELECT id, name, guild_id, product_id, url
                FROM kofi_products kp
                         LEFT JOIN product p ON kp.product_id = p.id
                WHERE ? = link_code
                """)
                .single(call().bind(name))
                .mapAs(Integer.class)
                .first();

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
