package de.chojo.lyna.data.dao.products.mailings;

import de.chojo.lyna.data.dao.products.Product;

import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Mailings {
    private final Product product;

    public Mailings(Product product) {
        this.product = product;
    }

    public Mailing create(String name, String mailText) {
        return builder(Mailing.class).query("""
                        INSERT INTO mail_products(product_id, name, mail_text) VALUES (?,?,?) RETURNING id
                        """)
                .parameter(stmt -> stmt.setInt(product.id()).setString(name).setString(mailText))
                .readRow(row -> new Mailing(row.getInt("id"), product, name, mailText))
                .firstSync()
                .get();
    }

    public Optional<Mailing> get() {
        return builder(Mailing.class)
                .query("""
                        SELECT id, product_id, name, mail_text FROM mail_products WHERE product_id = ?
                        """)
                .parameter(stmt -> stmt.setInt(product.id()))
                .readRow(row -> new Mailing(row.getInt("id"), product, row.getString("name"), row.getString("mail_text")))
                .firstSync();
    }
}
