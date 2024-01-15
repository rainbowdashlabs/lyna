package de.chojo.lyna.data.access;

import de.chojo.lyna.api.v1.kofi.payloads.KofiPost;
import de.chojo.lyna.api.v1.kofi.payloads.ShopItem;
import de.chojo.lyna.data.dao.products.Product;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class KoFiProducts {
    private final Products products;

    public KoFiProducts(Products products) {
        this.products = products;
    }

    public void create(Product product, String linkCode){
        builder()
                .query("""
                INSERT INTO kofi_products(link_code, product_id) VALUES (?,?)
                ON CONFLICT(link_code) DO UPDATE SET product_id = excluded.product_id;
                """)
                .parameter(stmt -> stmt.setString(linkCode).setInt(product.id()))
                .insert()
                .sendSync();
    }

    public Optional<Product> byCode(String name) {
        var id = builder(Integer.class)
                .query("""
                        SELECT id, name, guild_id, product_id, url
                        FROM kofi_products kp
                                 LEFT JOIN product p ON kp.product_id = p.id
                        WHERE ? = link_code
                        """)
                .parameter(stmt -> stmt.setString(name))
                .readRow(row -> row.getInt("id"))
                .firstSync();

        return id.flatMap(products::byId);
    }

    public void logTransaction(KofiPost post, String raw, @Nullable ShopItem item) {
        builder()
                .query("""
                        INSERT INTO kofi_sales(transaction_id, transaction_type, purchase_time, amount, currency, from_name, email, link_code, raw)
                        VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)""")
                .parameter(stmt -> stmt.setUuidAsString(post.kofiTransactionId())
                        .setEnum(post.type())
                        .setOffsetDateTime(post.timestamp())
                        .setFloat(post.amount())
                        .setString(post.currency())
                        .setString(post.from())
                        .setString(post.email())
                        .setString(item == null ? null : item.directLinkCode())
                        .setString(raw))
                .insert()
                .sendSync();
    }
}
