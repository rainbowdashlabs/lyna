/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.mail.repository;

import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.product.entity.Product;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Mailings {
    private final Product product;

    public Mailings(Product product) {
        this.product = product;
    }

    public Mailing create(String name, String mailText) {
        return query("""
                INSERT INTO mail_products(product_id, name, mail_text) VALUES (?,?,?) RETURNING id
                """)
                .single(call().bind(product.id()).bind(name).bind(mailText))
                .map(row -> new Mailing(row.getInt("id"), product, name, mailText))
                .first()
                .get();
    }

    public Optional<Mailing> get() {
        return query("""
                SELECT id, product_id, name, mail_text, blocks FROM mail_products WHERE product_id = ?
                """)
                .single(call().bind(product.id()))
                .map(row -> new Mailing(
                        row.getInt("id"),
                        product,
                        row.getString("name"),
                        row.getString("mail_text"),
                        row.getString("blocks")))
                .first();
    }
}
