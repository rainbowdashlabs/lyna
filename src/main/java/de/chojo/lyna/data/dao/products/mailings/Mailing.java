/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.dao.products.mailings;

import de.chojo.lyna.data.dao.products.Product;
import de.chojo.sadu.queries.api.call.Call;

import java.util.function.Function;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Mailing {
    private final int id;
    private final Product product;
    private String name;
    private String mailText;
    private String blocks;

    public Mailing(int id, Product product, String name, String mailText) {
        this(id, product, name, mailText, null);
    }

    public Mailing(int id, Product product, String name, String mailText, String blocks) {
        this.id = id;
        this.product = product;
        this.name = name;
        this.mailText = mailText;
        this.blocks = blocks;
    }

    public int id() {
        return id;
    }

    public Product product() {
        return product;
    }

    public String name() {
        return name;
    }

    public String mailText() {
        return mailText;
    }

    public void name(String name) {
        if (set("name", stmt -> stmt.bind(name))) {
            this.name = name;
        }
    }

    private boolean set(String column, Function<Call, Call> consumer) {
        return query("""
                UPDATE
                    mail_products
                SET %s = ?
                WHERE
                    id = ?""", column)
                .single(consumer.apply(call()).bind(id))
                .update()
                .changed();
    }

    /**
     * The mail as its operator composed it, or nothing for one written before blocks existed.
     *
     * @return the block document, as JSON
     */
    public String blocks() {
        return blocks;
    }

    public void blocks(String blocks) {
        if (query("""
                UPDATE mail_products SET blocks = ?::JSONB WHERE id = ?""").single(call().bind(blocks).bind(id)).update().changed()) {
            this.blocks = blocks;
        }
    }

    public void mailText(String mailText) {
        if (set("mail_text", stmt -> stmt.bind(mailText))) {
            this.mailText = mailText;
        }
    }
}
