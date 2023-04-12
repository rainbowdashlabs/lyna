package de.chojo.lyna.data.dao.products.mailings;

import de.chojo.lyna.data.dao.products.Product;
import de.chojo.sadu.exceptions.ThrowingConsumer;
import de.chojo.sadu.wrapper.util.ParamBuilder;

import java.sql.SQLException;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Mailing {
    private final int id;
    private final Product product;
    private String name;
    private String mailText;

    public Mailing(int id, Product product, String name, String mailText) {
        this.id = id;
        this.product = product;
        this.name = name;
        this.mailText = mailText;
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
        if (set("name", stmt -> stmt.setString(name))) {
            this.name = name;
        }
    }

    private boolean set(String column, ThrowingConsumer<ParamBuilder, SQLException> consumer) {
        return builder().query("""
                        UPDATE
                            mail_products
                        SET %s = ?
                        WHERE
                            id = ?""", column)
                .parameter(stmt -> {
                    consumer.accept(stmt);
                    stmt.setInt(id);
                }).update()
                .sendSync()
                .changed();
    }

    public void mailText(String mailText) {
        if (set("mail_text", stmt -> stmt.setString(mailText))) {
            this.mailText = mailText;
        }
    }
}
