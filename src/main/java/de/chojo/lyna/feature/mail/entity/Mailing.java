/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.mail.entity;

import de.chojo.lyna.feature.mail.repository.MailingRepository;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.sadu.queries.api.call.Call;

import java.util.function.Function;

public class Mailing {
    private static final MailingRepository REPOSITORY = new MailingRepository();

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
        return REPOSITORY.set(id, column, consumer);
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
        if (REPOSITORY.setBlocks(id, blocks)) {
            this.blocks = blocks;
        }
    }

    public void mailText(String mailText) {
        if (set("mail_text", stmt -> stmt.bind(mailText))) {
            this.mailText = mailText;
        }
    }
}
